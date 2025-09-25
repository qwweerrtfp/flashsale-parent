package com.ye94z.order.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.ye94z.order.mq.config.OrderMqConfig.*;

/** 订阅 payment-service 的“支付成功事件”，并接入统一 DLX 重试与全局 DLT */
@Configuration
public class PaymentEventMqConfig {
    /** 支付事件交换机 / 路由键（与 payment-service 发布端保持一致） */
    public static final String EX_PAYMENT_EVENTS = "payment.events";
    public static final String RK_PAYMENT_PAID   = "paid";

    /** 订单侧消费“支付成功事件”的主队列 */
    public static final String Q_ORDER_PAYMENT_PAID        = "order.payment.paid.q";
    /** 对应的重试队列（5s 后回到支付事件交换机/同一路由键） */
    public static final String Q_ORDER_PAYMENT_PAID_RETRY  = Q_ORDER_PAYMENT_PAID + ".retry";

    /** 支付事件交换机（topic） */
    @Bean
    public TopicExchange paymentEventExchange() {
        return ExchangeBuilder.topicExchange(EX_PAYMENT_EVENTS).durable(true).build();
    }

    /** 主队列：绑定统一 DLX，当消费失败（basicReject(false)）时路由到 EX_GLOBAL_DLX 的 "retry" */
    @Bean
    public Queue orderPaymentPaidQueue() {
        Map<String, Object> args = new HashMap<>();
        // 失败 -> DLX
        args.put("x-dead-letter-exchange", EX_GLOBAL_DLX);
        args.put("x-dead-letter-routing-key", RK_RETRY);
        return QueueBuilder.durable(Q_ORDER_PAYMENT_PAID).withArguments(args).build();
    }

    /** 绑定：支付事件交换机 -> 主队列（支付成功事件） */
    @Bean
    public Binding orderPaymentPaidBinding(TopicExchange paymentEventExchange,
                                           Queue orderPaymentPaidQueue) {
        return BindingBuilder.bind(orderPaymentPaidQueue).to(paymentEventExchange).with(RK_PAYMENT_PAID);
    }

    /** 重试队列：5s 到期后回原交换机/原路由键（再次投递“支付成功事件”） */
    @Bean
    public Queue orderPaymentPaidRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 5000);                     // 重试等待 5s
        args.put("x-dead-letter-exchange", EX_PAYMENT_EVENTS);  // 回到支付事件交换机
        args.put("x-dead-letter-routing-key", RK_PAYMENT_PAID); // 同一路由键
        return QueueBuilder.durable(Q_ORDER_PAYMENT_PAID_RETRY).withArguments(args).build();
    }

    /** 绑定：全局 DLX -> 本队列的重试队列（routingKey=retry） */
    @Bean
    public Binding orderPaymentPaidRetryBinding(
            @Qualifier("globalDlx") DirectExchange globalDlx,
            @Qualifier("orderPaymentPaidRetryQueue") Queue retryQ) {
        return BindingBuilder.bind(retryQ).to(globalDlx).with(RK_RETRY);
    }
}