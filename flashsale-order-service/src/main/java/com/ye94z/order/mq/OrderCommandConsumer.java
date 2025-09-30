package com.ye94z.order.mq;

import com.rabbitmq.client.Channel;
import com.ye94z.common.api.payment.PaymentApiClient;
import com.ye94z.common.api.product.ProductApiClient;
import com.ye94z.common.core.constants.RedisConstants;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.ye94z.common.core.constants.RedisConstants.ORDER_PERSISTED_KEY;
import static com.ye94z.order.mq.config.OrderMqConfig.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCommandConsumer {

    private final FlashOrderMapper orderMapper;
    private final ProductApiClient productApi;
    private final PaymentApiClient paymentApi;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

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
            order.setStatus(STATUS_UNPAID);

            orderMapper.insert(order);

            redisTemplate.opsForValue().set(ORDER_PERSISTED_KEY + order.getId(), "1", 60, TimeUnit.MINUTES);

            channel.basicAck(tag, false);
            log.info("[Create] order inserted: {}", order.getId());

            // 注：超时关单的延时消息已在下单入口发出，这里不重复发

        } catch (DuplicateKeyException e) {
            // 幂等命中（重复消息 / 同人同品唯一约束）
            channel.basicAck(tag, false);
            log.info("[Create] duplicate key, ignore. msg={}", msg);
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
                FlashOrder order = orderMapper.findById(msg.getOrderId());
                Integer quantity = order.getQuantity();
                // 回补 redis 库存
                redisTemplate.opsForValue().increment(RedisConstants.STOCK_PREFIX + order.getProductId(), quantity);
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
    public void onPay(PayOrderMessage msg, Message message, Channel channel,
                      @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1) 读取订单（处理“还没落库”的竞态）
            FlashOrder o = orderMapper.findById(msg.getOrderId());
            if (o == null) {
                // 让它走重试链路，等创建消费者把订单写好
                channel.basicReject(tag, false);
                return;
            }
            // 2) 归属 & 状态校验
            if (!o.getUserId().equals(msg.getUserId())) { channel.basicAck(tag, false); return; }
            if (o.getStatus() != 1 /*UNPAID*/) { log.info("[Pay] no-op (status changed, maybe paid): {}", msg.getOrderId());channel.basicAck(tag, false); return; }

            // 3) 调用支付（金额以落库后的金额为准，避免被篡改）
            long amount = o.getPayAmountCents();
            Result<Long> ret = paymentApi.pay(o.getUserId(), o.getId(), amount);
            log.info("[Pay] pay api ret: {}", ret);

            if (ret != null && ret.isSuccess()) {
                // 真正的状态变更仍由“支付成功事件”来驱动，这里只 ack
                channel.basicAck(tag, false);
                log.info("[Pay] debit ok, txnId={}", ret.getData());
            } else {
                // 简单错误分流：可按错误码/信息判断是否可重试
                String msgText = (ret == null ? "null" : ret.getErrorMsg());
                boolean retryable = msgText != null && (
                        msgText.contains("创建中") || msgText.contains("繁忙") || msgText.contains("稍后")
                );
                if (retryable) {
                    channel.basicReject(tag, false);  // 回原路由 → .retry → 再次到达
                } else {
                    channel.basicAck(tag, false);     // 不可重试直接吞
                }
                log.warn("[Pay] debit fail: {}", msgText);
            }
        } catch (Exception e) {
            int retries = OrderCommandConsumer.getRetryCount(xDeath);
            if (retries >= 2) {
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                channel.basicAck(tag, false);
            } else {
                channel.basicReject(tag, false);
            }
            log.error("[Pay] error, msg={}", msg, e);
        }
    }

    /**
     * 超时关单（延时队列）
     */
    @RabbitListener(queues = Q_ORDER_TIMEOUT)
    public void onTimeout(TimeoutOrderMessage msg,
                          Message message,
                          Channel channel,
                          @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String restoreKey = "restore_stock:" + msg.getOrderId();

        try {
            int n = orderMapper.updateStatusIf(
                    msg.getOrderId(), STATUS_UNPAID, STATUS_CANCELED, LocalDateTime.now());

            if (n > 0) {
                // 首次从 UNPAID -> CANCELED：做“只一次”的库存回补
                Boolean first = redisTemplate.opsForValue().setIfAbsent(restoreKey, "1", 1, TimeUnit.DAYS);
                if (Boolean.TRUE.equals(first)) {
                    redisTemplate.opsForValue().increment(RedisConstants.STOCK_PREFIX + msg.getProductId(), msg.getQuantity());
                }
                channel.basicAck(tag, false);
                log.info("[Timeout] canceled & restored, orderId={}", msg.getOrderId());
                return;
            }

            // 已支付等：直接 ack
            channel.basicAck(tag, false);
            log.info("[Timeout] no-op (paid or status changed): {}", msg.getOrderId());

        } catch (Exception e) {
            int retries = getRetryCount(xDeath);
            if (retries >= 2) {
                channel.basicAck(tag, false); // 终结当前消息
                // 投 DLT 留痕
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                log.error("[Timeout] error (to DLT), msg={}", msg, e);
            } else {
                // 让它进 DLX 的重试队列（前提：主队列配置了 DLX + RK_RETRY）
                channel.basicReject(tag, false); // 等价于 nack(requeue=false)
                log.warn("[Timeout] error, retry later. msg={}, retries={}", msg, retries, e);
            }
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