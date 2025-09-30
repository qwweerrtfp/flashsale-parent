package com.ye94z.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付事件交换机（仅发布端需要交换机；队列由 order-service 声明绑定）
 */
@Configuration
@Slf4j
public class PaymentMqConfig {
    public static final String EX_PAYMENT_EVENTS = "payment.events";
    public static final String RK_PAYMENT_PAID   = "paid";

    @Bean
    public TopicExchange paymentEventExchange() {
        return ExchangeBuilder.topicExchange(EX_PAYMENT_EVENTS).durable(true).build();
    }

    /** 1) 使用你全局的 ObjectMapper：Long→String、日期格式等策略会生效 */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        // 如需跨服务反序列化外部包模型，可放开受信包（生产建议精确到前缀）
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** 2) RabbitTemplate 走同一个 JSON Converter，并开启 Confirm/Return 便于排障 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        t.setMandatory(true); // unroutable 时触发 Return 回调

        t.setConfirmCallback((corr, ack, cause) -> {
            String id = corr != null ? corr.getId() : null;
            if (ack) {
                log.debug("Confirm OK, id={}", id);
            } else {
                log.error("Confirm NACK, id={}, cause={}", id, cause);
            }
        });

        t.setReturnsCallback(ret -> {
            String msgId = ret.getMessage().getMessageProperties().getMessageId();
            log.error("Return: msgId={}, code={}, text={}, ex={}, rk={}",
                    msgId, ret.getReplyCode(), ret.getReplyText(), ret.getExchange(), ret.getRoutingKey());
        });
        return t;
    }

    /** 3) 监听容器也用同一个 JSON Converter（关键！） */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory cf,
            MessageConverter mc) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, cf);
        f.setMessageConverter(mc);
        // f.setDefaultRequeueRejected(false); // 视重试/DLX策略需要
        return f;
    }


}