package com.ye94z.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 简化 Redisson 配置（单机）
 * 可在 application.yaml 配置 spring.data.redis.{host,port,password}
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config cfg = new Config();
        String addr = "redis://" + host + ":" + port;
        cfg.useSingleServer()
                .setAddress(addr)
                .setDatabase(database);
        if (password != null && !password.isBlank()) {
            cfg.useSingleServer().setPassword(password);
        }
        return Redisson.create(cfg);
    }
}