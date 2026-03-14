package com.ye94z.payment.mq;

import com.ye94z.common.core.pojo.PaymentPaidEventDTO;
import com.ye94z.payment.config.PaymentMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    /** 发布支付成功事件的消息模板。 */
    private final RabbitTemplate rabbitTemplate;

    public void publishPaid(PaymentPaidEventDTO event) {
        // 当前支付域只向外发布 paid 事件，订单服务会消费它并回写订单状态。
        rabbitTemplate.convertAndSend(
                PaymentMqConfig.EX_PAYMENT_EVENTS,
                PaymentMqConfig.RK_PAYMENT_PAID,
                event
        );
    }
}
