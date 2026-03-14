package com.ye94z.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单服务的 Redisson 配置。
 * 当前主链路里用得不多，但为后续分布式互斥、幂等控制等场景预留了能力。
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
