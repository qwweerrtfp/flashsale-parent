package com.ye94z.order.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 订单侧 MQ 基础设施配置。
 * 采用统一的“主队列 -> 全局 DLX -> 重试队列 -> 回原队列”模型，
 * 让取消、支付、超时关单三类消息共享同一套失败治理方式。
 */
@Configuration
@Slf4j
public class OrderMqConfig {

    /* === 订单命令与延时命令的交换机/路由键/队列定义 === */
    public static final String EX_ORDER_CMD    = "order.cmd.ex";
    public static final String EX_ORDER_DELAY  = "order.delay.ex";

    public static final String RK_ORDER_CANCEL  = "order.cancel";
    public static final String RK_ORDER_PAY     = "order.pay";
    public static final String RK_ORDER_TIMEOUT = "order.timeout";

    public static final String Q_ORDER_CANCEL  = "order.q.cancel";
    public static final String Q_ORDER_PAY     = "order.q.pay";
    public static final String Q_ORDER_TIMEOUT = "order.q.timeout";

    @Bean
    public TopicExchange orderCmdExchange() {
        return ExchangeBuilder.topicExchange(EX_ORDER_CMD).durable(true).build();
    }

    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "topic");
        return new CustomExchange(EX_ORDER_DELAY, "x-delayed-message", true, false, args);
    }

    /* === 统一死信交换机与最终失败队列 === */
    public static final String EX_GLOBAL_DLX = "order.dlx.ex";  // 全局死信交换机
    public static final String Q_GLOBAL_DLT  = "order.q.dlt";   // 统一最终失败队列
    public static final String RK_RETRY      = "retry";         // DLX -> 各重试队列
    public static final String RK_DLT        = "dlt";           // DLX -> DLT

    @Bean
    public DirectExchange globalDlx() {
        return ExchangeBuilder.directExchange(EX_GLOBAL_DLX).durable(true).build();
    }

    @Bean
    public Queue globalDltQueue() {
        return QueueBuilder.durable(Q_GLOBAL_DLT).build();
    }

    @Bean
    public Binding bindGlobalDlt(DirectExchange globalDlx, Queue globalDltQueue) {
        return BindingBuilder.bind(globalDltQueue).to(globalDlx).with(RK_DLT);
    }

    /* === 工具：为主队列挂上统一 DLX === */
    private Queue mainQueueWithDlx(String queueName) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EX_GLOBAL_DLX);
        args.put("x-dead-letter-routing-key", RK_RETRY);
        return QueueBuilder.durable(queueName).withArguments(args).build();
    }

    /* === 工具：创建重试队列，TTL 到期后回原交换机/原路由键 === */
    private Queue retryQueue(String retryQueueName, String deadLetterExchangeBack, String deadLetterRoutingKeyBack, int ttlMs) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ttlMs);
        args.put("x-dead-letter-exchange", deadLetterExchangeBack);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKeyBack);
        return QueueBuilder.durable(retryQueueName).withArguments(args).build();
    }

    /* === 取消订单 === */
    @Bean public Queue qOrderCancel() { return mainQueueWithDlx(Q_ORDER_CANCEL); }
    @Bean public Binding bindCancel(TopicExchange orderCmdExchange, Queue qOrderCancel) {
        return BindingBuilder.bind(qOrderCancel).to(orderCmdExchange).with(RK_ORDER_CANCEL);
    }
    public static final String Q_ORDER_CANCEL_RETRY = Q_ORDER_CANCEL + ".retry";
    @Bean public Queue qOrderCancelRetry() {
        return retryQueue(Q_ORDER_CANCEL_RETRY, EX_ORDER_CMD, RK_ORDER_CANCEL, 300);
    }
    @Bean public Binding bindCancelRetry(DirectExchange globalDlx, Queue qOrderCancelRetry) {
        return BindingBuilder.bind(qOrderCancelRetry).to(globalDlx).with(RK_RETRY);
    }

    /* === 支付命令 === */
    @Bean public Queue qOrderPay() { return mainQueueWithDlx(Q_ORDER_PAY); }
    @Bean public Binding bindPay(TopicExchange orderCmdExchange, Queue qOrderPay) {
        return BindingBuilder.bind(qOrderPay).to(orderCmdExchange).with(RK_ORDER_PAY);
    }
    public static final String Q_ORDER_PAY_RETRY = Q_ORDER_PAY + ".retry";
    @Bean public Queue qOrderPayRetry() {
        return retryQueue(Q_ORDER_PAY_RETRY, EX_ORDER_CMD, RK_ORDER_PAY, 300);
    }
    @Bean public Binding bindPayRetry(DirectExchange globalDlx, Queue qOrderPayRetry) {
        return BindingBuilder.bind(qOrderPayRetry).to(globalDlx).with(RK_RETRY);
    }

    /* === 超时关单（主队列仍绑定延时交换机；重试回延时交换机，但不再加延时） === */
    @Bean public Queue qOrderTimeout() { return mainQueueWithDlx(Q_ORDER_TIMEOUT); }
    @Bean public Binding bindTimeout(CustomExchange orderDelayExchange, Queue qOrderTimeout) {
        return BindingBuilder.bind(qOrderTimeout).to(orderDelayExchange).with(RK_ORDER_TIMEOUT).noargs();
    }
    public static final String Q_ORDER_TIMEOUT_RETRY = Q_ORDER_TIMEOUT + ".retry";
    @Bean public Queue qOrderTimeoutRetry() {
        return retryQueue(Q_ORDER_TIMEOUT_RETRY, EX_ORDER_DELAY, RK_ORDER_TIMEOUT, 300);
    }
    @Bean public Binding bindTimeoutRetry(DirectExchange globalDlx, Queue qOrderTimeoutRetry) {
        return BindingBuilder.bind(qOrderTimeoutRetry).to(globalDlx).with(RK_RETRY);
    }

    /** 使用统一 JSON 转换器，保证消息结构和 HTTP 层尽量一致。 */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        // 如需跨服务反序列化外部包模型，可放开受信包（生产建议精确到前缀）
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** 普通命令消息模板，打开 Confirm/Return 方便排障。 */
    @Bean
    @Primary
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

    @Bean("delayTemplate")
    public RabbitTemplate delayTemplate(ConnectionFactory cf, MessageConverter mc) {
        // 专门给延时交换机使用的模板。
        var t = new RabbitTemplate(cf); t.setMessageConverter(mc);
        t.setMandatory(false); // 直接禁用
        t.setConfirmCallback((corr, ack, cause) -> {
            String id = corr != null ? corr.getId() : null;
            if (ack) {
                log.debug("Confirm OK, id={}", id);
            } else {
                log.error("Confirm NACK, id={}, cause={}", id, cause);
            }
        });
        return t;
    }

    /** 监听容器统一走 JSON Converter，并显式使用手动 ACK。 */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory cf,
            MessageConverter mc) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, cf);
        f.setMessageConverter(mc);
        // 显式配置并发与预取，便于压测或排查堆积时调整。
        f.setConcurrentConsumers(4);       // 初始并发消费者数（每队列每实例）
        f.setMaxConcurrentConsumers(8);    // 峰值自动扩到 8
        f.setPrefetchCount(300);           // 每个消费者一次性抓 300 条放在本地缓冲

        // 消费者自己调用 basicAck/basicReject，因此这里必须是 MANUAL。
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        // 可选：每个消费者的线程名，便于日志排查
        // f.setTaskExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setName("order-consumer"); return t; }));
        // f.setDefaultRequeueRejected(false); // 视重试/DLX策略需要
        return f;
    }
}
