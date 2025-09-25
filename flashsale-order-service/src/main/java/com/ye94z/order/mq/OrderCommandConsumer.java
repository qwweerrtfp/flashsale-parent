package com.ye94z.order.mq;

import com.rabbitmq.client.Channel;
import com.ye94z.common.api.payment.PaymentApiClient;
import com.ye94z.common.api.product.ProductApiClient;
import com.ye94z.common.core.dto.ProductDTO;
import com.ye94z.common.core.dto.Result;
import com.ye94z.order.entity.FlashOrder;
import com.ye94z.order.mapper.FlashOrderMapper;
import com.ye94z.order.mq.config.OrderMqConfig;
import com.ye94z.order.mq.msg.CancelOrderMessage;
import com.ye94z.order.mq.msg.PayOrderMessage;
import com.ye94z.order.mq.msg.PlaceOrderMessage;
import com.ye94z.order.mq.msg.TimeoutOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.ye94z.order.mq.config.OrderMqConfig.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCommandConsumer {

    private final FlashOrderMapper orderMapper;
    private final ProductApiClient productApi;
    private final PaymentApiClient paymentApi;
    private final RabbitTemplate rabbitTemplate;

    private static final int STATUS_UNPAID = 1;
    private static final int STATUS_CANCELED = 4;

    /**
     * 创建订单：由 Consumer 落库，金额以 product-service 的 flashPrice 为准
     */
    @RabbitListener(queues = Q_ORDER_CREATE)
    public void onCreate(PlaceOrderMessage msg, Message message, Channel channel, @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1) 查询商品（以服务端价为准）
            Result<ProductDTO> prodRes = productApi.getProduct(msg.getProductId());
            if (prodRes == null || !prodRes.isSuccess() || prodRes.getData() == null) {
                // 业务无意义的重试，直接吞掉
                channel.basicAck(tag, false);
                log.warn("[Create] product not found or call failed: productId={}, res={}", msg.getProductId(), prodRes);
                return;
            }
            ProductDTO p = prodRes.getData();
            long flashPrice = p.getFlashPriceCents() == null ? 0L : p.getFlashPriceCents();
            long amount = (long) msg.getQuantity() * flashPrice;

            // 2) 构造订单并落库（幂等依赖唯一键）
            FlashOrder order = new FlashOrder();
            order.setId(msg.getOrderId());
            order.setUserId(msg.getUserId());
            order.setProductId(msg.getProductId());
            order.setQuantity(msg.getQuantity());
            order.setPayAmountCents(amount);
            order.setStatus((byte) STATUS_UNPAID);
            order.setCreateTime(LocalDateTime.now());

            orderMapper.insert(order);
            log.info("[Create] order inserted: {}", order.getId());

            // 注：超时关单的延时消息已在下单入口发出，这里不重复发

        } catch (DuplicateKeyException e) {
            // 幂等命中（重复消息 / 同人同品唯一约束）
            channel.basicAck(tag, false);
            log.info("[Create] dup key, ignore. msg={}", msg);
        } catch (Exception e) {
            int retries = getRetryCount(xDeath); // 已经走过 DLX 的次数
            if (retries >= 2) {
                // 最终失败：统一丢到 DLT
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                channel.basicAck(tag, false); // 确认已处理（原消息不再重投）
            } else {
                // 进入 DLX -> 各自 .retry 队列 -> TTL 后回主队列
                channel.basicReject(tag, false);
            }
        }
    }

    /**
     * 用户主动取消（仅未支付 -> 已取消），回补库存
     */
    @RabbitListener(queues = Q_ORDER_CANCEL)
    public void onCancel(CancelOrderMessage msg, Message message, Channel channel,@Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            int n = orderMapper.updateStatusIf(msg.getOrderId(), STATUS_UNPAID, STATUS_CANCELED, LocalDateTime.now());
            if (n > 0) {
                var db = orderMapper.findById(msg.getOrderId());
                if (db != null) {
                    // 回补库存（带上数量）
                    productApi.restoreStock(db.getProductId(), db.getQuantity(), db.getUserId());
                }
                channel.basicAck(tag, false);
                log.info("[Cancel] order canceled & stock restored: {}", msg.getOrderId());
            } else {
                // 幂等命中（重复消息）
                channel.basicAck(tag, false);
                log.info("[Cancel] no-op (status changed): {}", msg.getOrderId());
            }
        } catch (Exception e) {
            int retries = getRetryCount(xDeath);
            if (retries >= 2) {
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                channel.basicAck(tag, false);
            }else{
                channel.basicReject(tag, false);
            }
            log.error("[Cancel] error, msg={}", msg, e);
        }
    }

    /**
     * 发起支付（异步占位，真正扣款由 payment-service 完成）
     */
    @RabbitListener(queues = Q_ORDER_PAY)
    public void onPay(PayOrderMessage msg) {
        try {
            log.info("[Pay] received pay command, orderId={}", msg.getOrderId());
            paymentApi.pay(msg.getUserId(), msg.getOrderId(), msg.getPayAmountCents());
        } catch (Exception e) {
            log.error("[Pay] error, msg={}", msg, e);
        }
    }

    /**
     * 超时关单（延时队列）
     */
    @RabbitListener(queues = Q_ORDER_TIMEOUT)
    public void onTimeout(TimeoutOrderMessage msg, Message message, Channel channel,@Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            int n = orderMapper.updateStatusIf(msg.getOrderId(), STATUS_UNPAID, STATUS_CANCELED, LocalDateTime.now());
            if (n > 0) {
                // 回补库存：消息里已带齐数据，无需查库
                productApi.restoreStock(msg.getProductId(), msg.getQuantity(), msg.getUserId());
                channel.basicAck(tag, false);
                log.info("[Timeout] order canceled & stock restored: {}", msg.getOrderId());
            } else {
                // 幂等命中（重复消息）
                channel.basicAck(tag, false);
                log.info("[Timeout] no-op (status changed): {}", msg.getOrderId());
            }
        } catch (Exception e) {
            int retries = getRetryCount(xDeath);
            if (retries >= 2) {
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
            }else{
                channel.basicReject(tag, false);
            }
            log.error("[Timeout] error, msg={}", msg, e);
        }
    }

    public static int getRetryCount(List<Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) return 0;
        long total = 0;
        for (Map<String, Object> h : xDeath) {
            Object c = h.get("count");
            if (c instanceof Number n) total += n.longValue();
        }
        return (int) total;
    }
}