package com.ye94z.payment.mq;

import com.ye94z.payment.config.PaymentMqConfig;
import com.ye94z.payment.event.PaymentPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishPaid(PaymentPaidEvent event) {
        rabbitTemplate.convertAndSend(
                PaymentMqConfig.EX_PAYMENT_EVENTS,
                PaymentMqConfig.RK_PAYMENT_PAID,
                event
        );
    }
}