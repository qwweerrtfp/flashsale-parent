package com.ye94z.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        // 启动支付服务，负责余额扣减、支付流水记录和支付成功事件发布。
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
