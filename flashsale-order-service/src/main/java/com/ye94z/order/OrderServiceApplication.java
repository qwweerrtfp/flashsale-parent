package com.ye94z.order;

import com.ye94z.common.api.config.FeignCommonConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.ye94z.common.api",
        defaultConfiguration = FeignCommonConfig.class) // ← 来自 common-api
@EnableRabbit
@EnableDiscoveryClient
public class OrderServiceApplication {
    public static void main(String[] args) {
        // 订单服务是整个系统的编排中心，会同时依赖 Feign、RabbitMQ 和服务发现。
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
