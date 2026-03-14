package com.ye94z.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品服务启动入口。
 * 扫描范围放到 com.ye94z，确保 common 模块里的共享组件也能被本服务直接使用。
 */
@SpringBootApplication(scanBasePackages = "com.ye94z")
public class ProductServiceApplication {

    public static void main(String[] args) {
        // 启动商品服务，负责商品查询、秒杀闸口与库存回补。
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
