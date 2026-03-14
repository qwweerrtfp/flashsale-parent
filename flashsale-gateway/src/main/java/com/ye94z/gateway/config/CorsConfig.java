package com.ye94z.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        // 注意：允许携带凭证时不能直接写 allowedOrigins("*")，
        // 否则浏览器会因为规范限制而拒绝响应，这里改用 pattern 方式。
        cfg.addAllowedOriginPattern("*");
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setAllowCredentials(true); // 允许携带 Cookie / Authorization 等凭证信息

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对整个网关下的所有路径统一生效。
        source.registerCorsConfiguration("/**", cfg);
        return new CorsWebFilter(source);
    }
}
