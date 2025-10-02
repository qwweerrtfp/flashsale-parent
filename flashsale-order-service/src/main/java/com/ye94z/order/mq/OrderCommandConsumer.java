package com.ye94z.order.mq;

import com.rabbitmq.client.Channel;
import com.ye94z.common.api.payment.PaymentApiClient;
import com.ye94z.common.api.product.ProductApiClient;
import com.ye94z.common.core.constants.RedisConstants;
import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
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
import java.util.concurrent.TimeUnit;

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
     * 用户主动取消（仅未支付 -> 已取消），回补库存
     */
    @RabbitListener(queues = Q_ORDER_CANCEL)
    public void onCancel(CancelOrderMessage msg, Message message, Channel channel, @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            int n = orderMapper.updateStatusIf(
                    msg.getOrderId(), STATUS_UNPAID, STATUS_CANCELED, LocalDateTime.now());

            if (n > 0) {
                Result result = productApi.restoreStock(msg.getProductId(), msg.getUserId(), msg.getQuantity());
                if (result.isSuccess()) {
                    log.info("[Cancel] restored stock, orderId={}", msg.getOrderId());
                } else {
                    log.warn("[Cancel] restore stock fail, maybe restored, orderId={}", msg.getOrderId());
                }
                channel.basicAck(tag, false);
                log.info("[Cancel] canceled & restored, orderId={}", msg.getOrderId());
            }
        } catch (Exception e) {
            int retries = getRetryCount(xDeath);
            if (retries >= 2) {
                channel.basicAck(tag, false); // 终结当前消息
                // 投 DLT 留痕
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                log.error("[Cancel] error (to DLT), msg={}", msg, e);
            } else {
                // 让它进 DLX 的重试队列（前提：主队列配置了 DLX + RK_RETRY）
                channel.basicReject(tag, false); // 等价于 nack(requeue=false)
                log.warn("[Cancel] error, retry later. msg={}, retries={}", msg, retries, e);
            }
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
            // 1) 读取订单
            FlashOrder o = orderMapper.findById(msg.getOrderId());
            if (o == null) {
                // 让它走重试链路
                channel.basicReject(tag, false);
                return;
            }
            // 2) 归属 & 状态校验
            if (!o.getUserId().equals(msg.getUserId())) {
                channel.basicAck(tag, false);
                return;
            }
            if (o.getStatus() != 1 /*UNPAID*/) {
                log.info("[Pay] no-op (status changed, maybe paid): {}", msg.getOrderId());
                channel.basicAck(tag, false);
                return;
            }

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
                boolean retryable = msgText != null && (msgText.contains("繁忙") || msgText.contains("稍后"));
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

        try {
            int n = orderMapper.updateStatusIf(
                    msg.getOrderId(), STATUS_UNPAID, STATUS_CANCELED, LocalDateTime.now());

            if (n > 0) {
                Result result = productApi.restoreStock(msg.getProductId(), msg.getUserId(), msg.getQuantity());
                if (result.isSuccess()) {
                    log.info("[Timeout] restored stock, orderId={}", msg.getOrderId());
                } else {
                    log.warn("[Timeout] restore stock fail, maybe restored, orderId={}", msg.getOrderId());
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