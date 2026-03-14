package com.ye94z.order.mq;

import com.rabbitmq.client.Channel;
import com.ye94z.common.core.pojo.PaymentPaidEventDTO;
import com.ye94z.order.mapper.FlashOrderMapper;
import com.ye94z.order.mq.config.OrderMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.ye94z.order.mq.config.PaymentEventMqConfig.Q_ORDER_PAYMENT_PAID;

/**
 * 订单侧消费“支付成功事件”，将 UNPAID -> PAID，并写入 pay_txn_id / pay_time / pay_channel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final FlashOrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = Q_ORDER_PAYMENT_PAID)
    public void onPaid(PaymentPaidEventDTO evt, Message message, Channel channel, @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // 只允许未支付订单切换为已支付，重复事件或已取消订单都会自然 no-op。
            int n = orderMapper.markPaidIfUnpaid(evt.getOrderId(), evt.getTxnId(), (byte)1 /*BALANCE*/);
            if (n > 0) {
                log.info("[Order] mark PAID success, orderId={}, txnId={}", evt.getOrderId(), evt.getTxnId());
            } else {
                log.info("[Order] mark PAID no-op, maybe already paid/canceled, orderId={}", evt.getOrderId());
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            int retries = OrderCommandConsumer.getRetryCount(xDeath);
            if (retries >= 2) {
                // 支付成功事件非常关键，最终失败要保留到 DLT 便于人工补偿。
                log.error("[Order] handle payment paid event error - finally, evt={}", evt, e);
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
                channel.basicAck(tag, false);
            } else {
                log.error("[Order] handle payment paid event error, evt={}", evt, e);
                channel.basicReject(tag, false);
            }
        }
    }
}
