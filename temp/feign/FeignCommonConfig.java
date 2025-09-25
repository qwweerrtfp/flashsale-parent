package com.ye94z.order.feign;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通用的 Feign 配置：
 *  - FULL 日志便于联调（生产可调为 BASIC/NONE）
 *  - 透传 Authorization 头，便于下游做鉴权/审计
 */
@Configuration
public class FeignCommonConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL; // 开发期建议 FULL，生产可改低
    }

    @Bean
    public RequestInterceptor authHeaderForwardInterceptor() {
        return template -> {
            // 从当前请求上下文中取 Authorization 头并向下游透传
            // 注意：需要在网关或入口处把 token 放到 Header
            String auth = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof
                    org.springframework.web.context.request.ServletRequestAttributes attrs
                    ? attrs.getRequest().getHeader("Authorization") : null;

            if (auth != null && !auth.isBlank()) {
                template.header("Authorization", auth);
            }
        };
    }
}