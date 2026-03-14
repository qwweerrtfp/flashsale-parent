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

    /** 订单表访问层。 */
    private final FlashOrderMapper orderMapper;
    /** 商品服务 Feign。 */
    private final ProductApiClient productApi;
    /** 支付服务 Feign。 */
    private final PaymentApiClient paymentApi;
    /** 用于把最终失败消息送入 DLT。 */
    private final RabbitTemplate rabbitTemplate;
    /** 预留 Redis 访问能力。 */
    private final StringRedisTemplate redisTemplate;

    private static final int STATUS_UNPAID = 1;
    private static final int STATUS_CANCELED = 4;

    /** 处理主动取消命令，成功后回补库存。 */
    @RabbitListener(queues = Q_ORDER_CANCEL)
    public void onCancel(CancelOrderMessage msg, Message message, Channel channel, @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // 只有状态仍是 UNPAID 时，才允许切换到 CANCELED。
            int n = orderMapper.updateStatusIf(
                    msg.getOrderId(), STATUS_UNPAID, STATUS_CANCELED, LocalDateTime.now());

            if (n > 0) {
                // 订单取消成功后，通知商品服务释放库存名额。
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
                channel.basicAck(tag, false);
                // 超过重试上限后写入最终死信队列，便于人工排查。
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                log.error("[Cancel] error (to DLT), msg={}", msg, e);
            } else {
                // 走统一 DLX -> retry 队列 -> 回原队列的重试链路。
                channel.basicReject(tag, false);
                log.warn("[Cancel] error, retry later. msg={}, retries={}", msg, retries, e);
            }
        }
    }

    /** 处理支付命令，读取订单并调用 payment-service 实际扣款。 */
    @RabbitListener(queues = Q_ORDER_PAY)
    public void onPay(PayOrderMessage msg, Message message, Channel channel,
                      @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1) 消费时再读取订单，避免和下单事务时序冲突。
            FlashOrder o = orderMapper.findById(msg.getOrderId());
            if (o == null) {
                // 订单暂时不可见时先走重试，给数据库提交留时间。
                channel.basicReject(tag, false);
                return;
            }
            // 2) 校验订单归属和状态，避免替别人支付或重复支付。
            if (!o.getUserId().equals(msg.getUserId())) {
                channel.basicAck(tag, false);
                return;
            }
            if (o.getStatus() != 1 /*UNPAID*/) {
                log.info("[Pay] no-op (status changed, maybe paid): {}", msg.getOrderId());
                channel.basicAck(tag, false);
                return;
            }

            // 3) 金额以订单表里固化的金额为准，不信任外部输入。
            long amount = o.getPayAmountCents();
            Result<Long> ret = paymentApi.pay(o.getUserId(), o.getId(), amount);
            log.info("[Pay] pay api ret: {}", ret);

            if (ret != null && ret.isSuccess()) {
                // 真正的订单状态回写仍由支付成功事件驱动，这里只确认支付命令已处理。
                channel.basicAck(tag, false);
                log.info("[Pay] debit ok, txnId={}", ret.getData());
            } else {
                // 简单按错误文案区分是否值得重试。
                String msgText = (ret == null ? "null" : ret.getErrorMsg());
                boolean retryable = msgText != null && (msgText.contains("繁忙") || msgText.contains("稍后"));
                if (retryable) {
                    channel.basicReject(tag, false);
                } else {
                    channel.basicAck(tag, false);
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

    /** 处理延时关单消息，超时未支付时关闭订单并回补库存。 */
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
                // 只有订单真的从未支付变成已取消，才进行库存补偿。
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

            // 已支付或已被其他流程处理时，超时消息不再介入。
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
            // RabbitMQ 会在 x-death 头里记录每次死信次数，这里做累加。
            Object c = h.get("count");
            if (c instanceof Number n) total += n.longValue();
        }
        return (int) total;
    }
}
