package com.ye94z.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(org.redisson.Redisson.class)
@ConditionalOnProperty(prefix="app.redis", name="enabled", havingValue="true")
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 当前主要给缓存重建场景提供分布式锁能力。
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        // 示例环境直接使用单机 Redis 配置即可。
        return Redisson.create(config);
    }
}
