package com.ye94z.order.mq;

import com.ye94z.order.mq.config.OrderMqConfig;
import com.ye94z.order.mq.msg.CancelOrderMessage;
import com.ye94z.order.mq.msg.PayOrderMessage;
import com.ye94z.order.mq.msg.PlaceOrderMessage;
import com.ye94z.order.mq.msg.TimeoutOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderCommandProducer {

    /** 普通命令消息模板。 */
    private final RabbitTemplate rabbitTemplate;

    /** 专门用于延时交换机的消息模板。 */
    @Qualifier("delayTemplate")
    private final RabbitTemplate delayTemplate;

    public OrderCommandProducer(RabbitTemplate rabbitTemplate, @Qualifier("delayTemplate") RabbitTemplate delayTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.delayTemplate = delayTemplate;
    }

    public void sendCancel(CancelOrderMessage msg) {
        MessagePostProcessor mpp = m -> {
            // 配置消息持久化和 messageId，方便 Broker 持久保存与排障。
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            m.getMessageProperties().setMessageId(String.valueOf(msg.getOrderId()));
            return m;
        };
        rabbitTemplate.convertAndSend(OrderMqConfig.EX_ORDER_CMD, OrderMqConfig.RK_ORDER_CANCEL, msg, mpp);
        log.info("[MQ] send cancel: {}", msg);
    }

    public void sendPay(PayOrderMessage msg) {
        MessagePostProcessor mpp = m -> {
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            m.getMessageProperties().setMessageId(String.valueOf(msg.getOrderId()));
            return m;
        };
        rabbitTemplate.convertAndSend(OrderMqConfig.EX_ORDER_CMD, OrderMqConfig.RK_ORDER_PAY, msg, mpp);
        log.info("[MQ] send pay: {}", msg);
    }

    /** 延时关单，通过 x-delayed-message 插件控制延迟投递。 */
    public void sendTimeout(TimeoutOrderMessage msg, long delayMs) {
        MessagePostProcessor mpp = m -> {
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            m.getMessageProperties().setMessageId(String.valueOf(msg.getOrderId()));
            // x-delay 是 delayed-message 插件识别的延迟毫秒数。
            m.getMessageProperties().setHeader("x-delay", delayMs);
            return m;
        };
        delayTemplate.convertAndSend(OrderMqConfig.EX_ORDER_DELAY, OrderMqConfig.RK_ORDER_TIMEOUT, msg, mpp);
        log.info("[MQ] schedule timeout: {} ms, {}", delayMs, msg);
    }
}
