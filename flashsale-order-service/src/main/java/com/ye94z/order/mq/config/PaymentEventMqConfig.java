package com.ye94z.order.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.ye94z.order.mq.config.OrderMqConfig.*;

/**
 * 订单侧订阅支付成功事件的队列与重试配置。
 * 支付服务只负责发事件，订单服务自己声明消费队列和重试链路。
 */
@Configuration
public class PaymentEventMqConfig {
    /** 支付事件交换机 / 路由键（与 payment-service 发布端保持一致） */
    public static final String EX_PAYMENT_EVENTS = "payment.events";
    public static final String RK_PAYMENT_PAID   = "paid";

    /** 订单侧消费“支付成功事件”的主队列 */
    public static final String Q_ORDER_PAYMENT_PAID        = "order.payment.paid.q";
    /** 对应的重试队列（5s 后回到支付事件交换机/同一路由键） */
    public static final String Q_ORDER_PAYMENT_PAID_RETRY  = Q_ORDER_PAYMENT_PAID + ".retry";

    /** 支付事件交换机，名称需要和支付服务发布端保持一致。 */
    @Bean
    public TopicExchange paymentEventExchange() {
        return ExchangeBuilder.topicExchange(EX_PAYMENT_EVENTS).durable(true).build();
    }

    /** 主队列：消费失败时通过全局 DLX 进入重试队列。 */
    @Bean
    public Queue orderPaymentPaidQueue() {
        Map<String, Object> args = new HashMap<>();
        // 失败 -> DLX
        args.put("x-dead-letter-exchange", EX_GLOBAL_DLX);
        args.put("x-dead-letter-routing-key", RK_RETRY);
        return QueueBuilder.durable(Q_ORDER_PAYMENT_PAID).withArguments(args).build();
    }

    /** 把支付成功事件绑定到订单侧消费队列。 */
    @Bean
    public Binding orderPaymentPaidBinding(TopicExchange paymentEventExchange,
                                           Queue orderPaymentPaidQueue) {
        return BindingBuilder.bind(orderPaymentPaidQueue).to(paymentEventExchange).with(RK_PAYMENT_PAID);
    }

    /** 重试队列：TTL 到期后回到 payment.events/paid。 */
    @Bean
    public Queue orderPaymentPaidRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 300);                     // 重试等待 5s
        args.put("x-dead-letter-exchange", EX_PAYMENT_EVENTS);  // 回到支付事件交换机
        args.put("x-dead-letter-routing-key", RK_PAYMENT_PAID); // 同一路由键
        return QueueBuilder.durable(Q_ORDER_PAYMENT_PAID_RETRY).withArguments(args).build();
    }

    /** 把全局 DLX 的 retry 路由键绑定到支付事件重试队列。 */
    @Bean
    public Binding orderPaymentPaidRetryBinding(
            @Qualifier("globalDlx") DirectExchange globalDlx,
            @Qualifier("orderPaymentPaidRetryQueue") Queue retryQ) {
        return BindingBuilder.bind(retryQ).to(globalDlx).with(RK_RETRY);
    }
}
