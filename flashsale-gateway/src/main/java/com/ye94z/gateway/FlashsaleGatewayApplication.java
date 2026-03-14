package com.ye94z.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlashsaleGatewayApplication {
    public static void main(String[] args) {
        // 启动网关服务。网关是整个系统的统一入口，通常最后启动也没问题。
        SpringApplication.run(FlashsaleGatewayApplication.class, args);
    }
}
