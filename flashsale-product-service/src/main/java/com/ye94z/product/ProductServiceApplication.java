package com.ye94z.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product 服务启动类
 *
 * 放置路径：
 *   src/main/java/com/ye94z/product/ProductServiceApplication.java
 *
 * 说明：
 *  - scanBasePackages = "com.ye94z" 让本服务能扫描到 common 模块中的 @Component（例如 CacheClient）。
 *  - @MapperScan 指定本服务的 MyBatis Mapper 包（按你的 mapper 包路径修改）。
 *  - @EnableDiscoveryClient 开启 Nacos 注册发现（如果你已接入 Nacos）。
 */

@SpringBootApplication(scanBasePackages = "com.ye94z")
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}