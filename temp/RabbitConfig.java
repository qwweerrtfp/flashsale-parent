package config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.naming.Binding;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

@Slf4j
@Configuration
public class RabbitConfig {

    /** 主交换机（topic） */
    public static final String EX_ORDER = "fs.order.exchange";
    /** 死信交换机（direct） */
    public static final String EX_ORDER_DLX = "fs.order.dlx";
    /** 延时交换机（x-delayed-message 插件，底层 direct） */
    public static final String EX_DELAY = "fs.order.delay";

    /** 主路由键：新订单 */
    public static final String RK_ORDER_MAIN = "order.create";
    /** 死信路由键：重试（流入重试队列，等待 TTL 后回到主交换机） */
    public static final String RK_ORDER_RETRY = "order.retry";
    /** 死信路由键：最终死信（人工介入/告警） */
    public static final String RK_ORDER_DLT = "order.dlt";
    /** 延时路由键：超时关单 */
    public static final String RK_ORDER_TIMEOUT = "order.timeout";

    /** 主队列 */
    public static final String Q_ORDER_MAIN = "fs.order.q";
    /** 重试队列（DLX 投递到这里，TTL 到期后再投回主交换机） */
    public static final String Q_ORDER_RETRY = "fs.order.q.retry";
    /** 最终死信队列 */
    public static final String Q_ORDER_DLT = "fs.order.q.dlt";
    /** 延时关单队列 */
    public static final String Q_ORDER_TIMEOUT = "fs.order.q.timeout";

    /* -------------------- 基础组件 -------------------- */

    /** 让 RabbitTemplate 使用全局 ObjectMapper（Long→String 等策略也会生效） */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** 配好 Confirm/Return，便于观测生产端投递链路 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        t.setMandatory(true); // unroutable 时触发 Return 回调

        // Confirm：是否到达交换机
        t.setConfirmCallback((corr, ack, cause) -> {
            String id = corr != null ? corr.getId() : null;
            if (ack) {
                log.debug("Rabbit Confirm OK, id={}", id);
            } else {
                log.error("Rabbit Confirm NACK, id={}, cause={}", id, cause);
            }
        });

        // Return：到达交换机但无法路由到任何队列
        t.setReturnsCallback(ret -> {
            String msgId = ret.getMessage().getMessageProperties().getMessageId();
            log.error("Rabbit Return, msgId={}, code={}, text={}, exchange={}, rk={}",
                    msgId, ret.getReplyCode(), ret.getReplyText(), ret.getExchange(), ret.getRoutingKey());
        });
        return t;
    }

    /* -------------------- 交换机 -------------------- */

    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder.topicExchange(EX_ORDER).durable(true).build();
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return ExchangeBuilder.directExchange(EX_ORDER_DLX).durable(true).build();
    }

    /** 需要安装延时交换机插件：rabbitmq_delayed_message_exchange */
    @Bean
    public CustomExchange delayExchange() {
        Map<String, Object> args = new HashMap<>();
        // 指定内部实际路由类型（direct）
        args.put("x-delayed-type", "direct");
        return new CustomExchange(EX_DELAY, "x-delayed-message", true, false, args);
    }

    /* -------------------- 队列 -------------------- */

    /** 主队列：失败时死信到 DLX，路由键固定为 RK_ORDER_RETRY */
    @Bean
    public Queue orderQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EX_ORDER_DLX);
        args.put("x-dead-letter-routing-key", RK_ORDER_RETRY);
        return QueueBuilder.durable(Q_ORDER_MAIN).withArguments(args).build();
    }

    /** 重试队列：消息在此等待 TTL，过期后投回主交换机（再进入主队列） */
    @Bean
    public Queue retryQueue() {
        Map<String, Object> args = new HashMap<>();
        // 统一等待 5s，你也可以做多级重试（retry-5s、retry-30s、retry-2m …）
        args.put("x-message-ttl", 5000);
        args.put("x-dead-letter-exchange", EX_ORDER);
        args.put("x-dead-letter-routing-key", RK_ORDER_MAIN);
        return QueueBuilder.durable(Q_ORDER_RETRY).withArguments(args).build();
    }

    /** 最终死信队列：重试超限或不可恢复异常，投这里 */
    @Bean
    public Queue dltQueue() {
        return QueueBuilder.durable(Q_ORDER_DLT).build();
    }

    /** 延时关单队列：由延时交换机投递 */
    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(Q_ORDER_TIMEOUT).build();
    }

    /* -------------------- 绑定 -------------------- */

    @Bean
    public Binding orderBinding(TopicExchange orderExchange, Queue orderQueue) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(RK_ORDER_MAIN);
    }

    @Bean
    public Binding retryBinding(DirectExchange orderDlxExchange, Queue retryQueue) {
        return BindingBuilder.bind(retryQueue).to(orderDlxExchange).with(RK_ORDER_RETRY);
    }

    @Bean
    public Binding dltBinding(DirectExchange orderDlxExchange, Queue dltQueue) {
        return BindingBuilder.bind(dltQueue).to(orderDlxExchange).with(RK_ORDER_DLT);
    }

    @Bean
    public Binding timeoutBinding(CustomExchange delayExchange, Queue timeoutQueue) {
        // CustomExchange 绑定时也用 with(routingKey)
        return BindingBuilder.bind(timeoutQueue).to(delayExchange).with(RK_ORDER_TIMEOUT).noargs();
    }
}