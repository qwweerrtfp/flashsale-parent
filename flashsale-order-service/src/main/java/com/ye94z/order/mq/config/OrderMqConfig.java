package com.ye94z.order.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一重试：所有主队列失败 -> DLX -> 各自重试队列(5s TTL) -> 回到原交换机/原路由键
 * 重试次数>=2 后由消费者转发到统一 DLT。
 */
@Configuration
public class OrderMqConfig {

    /* === 你已有的定义，保持不变 === */
    public static final String EX_ORDER_CMD    = "order.cmd.ex";
    public static final String EX_ORDER_DELAY  = "order.delay.ex";

    public static final String RK_ORDER_CREATE  = "order.create";
    public static final String RK_ORDER_CANCEL  = "order.cancel";
    public static final String RK_ORDER_PAY     = "order.pay";
    public static final String RK_ORDER_TIMEOUT = "order.timeout";

    public static final String Q_ORDER_CREATE  = "order.q.create";
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

    /* === 统一 DLX & DLT（新增） === */
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

    /* === 工具：给主队列套上 DLX，返回主队列实例 === */
    private Queue mainQueueWithDlx(String queueName) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EX_GLOBAL_DLX);
        args.put("x-dead-letter-routing-key", RK_RETRY);
        return QueueBuilder.durable(queueName).withArguments(args).build();
    }

    /* === 工具：为某主路由创建“重试队列”，TTL到期后回原交换机/原路由键 === */
    private Queue retryQueue(String retryQueueName, String deadLetterExchangeBack, String deadLetterRoutingKeyBack, int ttlMs) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ttlMs);
        args.put("x-dead-letter-exchange", deadLetterExchangeBack);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKeyBack);
        return QueueBuilder.durable(retryQueueName).withArguments(args).build();
    }

    /* === 创建订单：主队列 + 绑定到命令交换机 === */
    @Bean public Queue qOrderCreate() { return mainQueueWithDlx(Q_ORDER_CREATE); }
    @Bean public Binding bindCreate(TopicExchange orderCmdExchange, Queue qOrderCreate) {
        return BindingBuilder.bind(qOrderCreate).to(orderCmdExchange).with(RK_ORDER_CREATE);
    }
    // 对应重试队列：5s 后回 EX_ORDER_CMD + RK_ORDER_CREATE
    public static final String Q_ORDER_CREATE_RETRY = Q_ORDER_CREATE + ".retry";
    @Bean public Queue qOrderCreateRetry() {
        return retryQueue(Q_ORDER_CREATE_RETRY, EX_ORDER_CMD, RK_ORDER_CREATE, 5000);
    }
    @Bean public Binding bindCreateRetry(DirectExchange globalDlx, Queue qOrderCreateRetry) {
        return BindingBuilder.bind(qOrderCreateRetry).to(globalDlx).with(RK_RETRY);
    }

    /* === 取消订单 === */
    @Bean public Queue qOrderCancel() { return mainQueueWithDlx(Q_ORDER_CANCEL); }
    @Bean public Binding bindCancel(TopicExchange orderCmdExchange, Queue qOrderCancel) {
        return BindingBuilder.bind(qOrderCancel).to(orderCmdExchange).with(RK_ORDER_CANCEL);
    }
    public static final String Q_ORDER_CANCEL_RETRY = Q_ORDER_CANCEL + ".retry";
    @Bean public Queue qOrderCancelRetry() {
        return retryQueue(Q_ORDER_CANCEL_RETRY, EX_ORDER_CMD, RK_ORDER_CANCEL, 5000);
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
        return retryQueue(Q_ORDER_PAY_RETRY, EX_ORDER_CMD, RK_ORDER_PAY, 5000);
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
        return retryQueue(Q_ORDER_TIMEOUT_RETRY, EX_ORDER_DELAY, RK_ORDER_TIMEOUT, 5000);
    }
    @Bean public Binding bindTimeoutRetry(DirectExchange globalDlx, Queue qOrderTimeoutRetry) {
        return BindingBuilder.bind(qOrderTimeoutRetry).to(globalDlx).with(RK_RETRY);
    }
}