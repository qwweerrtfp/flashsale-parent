package com.ye94z.order.mq;

import com.rabbitmq.client.Channel;
import com.ye94z.order.event.PaymentPaidEvent;
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
import java.time.LocalDateTime;
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
    public void onPaid(PaymentPaidEvent evt, Message  message, Channel channel, @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
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
                rabbitTemplate.send(OrderMqConfig.EX_GLOBAL_DLX, OrderMqConfig.RK_DLT, message);
            } else {
                channel.basicReject(tag, false);
            }
            log.error("[Order] handle payment paid event error, evt={}", evt, e);
        }
    }
}