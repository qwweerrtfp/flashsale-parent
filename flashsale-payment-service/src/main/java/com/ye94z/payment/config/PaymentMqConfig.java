package com.ye94z.payment.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付事件交换机（仅发布端需要交换机；队列由 order-service 声明绑定）
 */
@Configuration
public class PaymentMqConfig {
    public static final String EX_PAYMENT_EVENTS = "payment.events";
    public static final String RK_PAYMENT_PAID   = "paid";

    @Bean
    public TopicExchange paymentEventExchange() {
        return ExchangeBuilder.topicExchange(EX_PAYMENT_EVENTS).durable(true).build();
    }
}