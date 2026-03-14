package com.ye94z.common.api.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 所有 Feign 客户端共用的配置。
 * 这类配置放在 common-api 里有两个目的：
 * 1. 避免每个服务都重复声明 Feign 日志级别和拦截器；
 * 2. 保证服务间转发请求时，请求头透传策略保持一致。
 */
@Configuration
public class FeignCommonConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        // 开发阶段把请求/响应细节全部打出来，联调最方便。
        // 如果后续日志量过大，可以降到 BASIC 或 NONE。
        return Logger.Level.FULL; // 开发期建议 FULL，生产可改低
    }

    @Bean
    public RequestInterceptor authHeaderForwardInterceptor() {
        return template -> {
            // 从当前线程绑定的 HTTP 请求上下文中拿到 Authorization。
            // 这样 order-service 在调用 product/payment 时，可以把原始 token
            // 原样转发给下游，便于下游在必要时继续做审计或兼容性校验。
            String auth = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof
                    org.springframework.web.context.request.ServletRequestAttributes attrs
                    ? attrs.getRequest().getHeader("Authorization") : null;

            if (auth != null && !auth.isBlank()) {
                // 只有上游请求里真的带了 Authorization 才追加，避免给下游写入空值。
                template.header("Authorization", auth);
            }
        };
    }
}
