package com.ye94z.gateway.filter;

import com.ye94z.payment.security.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // 最小白名单（按你项目接口调整）
    private static final Set<String> WHITE_LIST = Set.of(
            "/api/user/login",
            "/api/user/send-code",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // 命中白名单 -> 放行
        if (isWhite(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(auth)) {
            return unauthorized(exchange.getResponse(), "Missing Authorization");
        }

        String token = auth;
        if (auth.toLowerCase().startsWith("bearer ")) {
            token = auth.substring(7);
        }

        Claims claims = JwtUtils.parseToken(token);
        if (claims == null) {
            return unauthorized(exchange.getResponse(), "Invalid or expired token");
        }

        // 从 claims 取出你签发时放入的字段（与你的 JwtUtil.generateToken 对齐）
        Long userId = claims.get("userId", Long.class);
        String nickName = claims.get("nickName", String.class);
        String icon = claims.get("icon", String.class);

        // 透传到下游（用自定义请求头，避免被网关或下游框架屏蔽）
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Nick");
                    h.remove("X-User-Icon");
                    // 之后再由网关注入
                    if (userId != null) h.set("X-User-Id", String.valueOf(userId));
                    if (StringUtils.hasText(nickName)) h.set("X-User-Nick", nickName);
                    if (StringUtils.hasText(icon)) h.set("X-User-Icon", icon);
                }))
                .build();

        return chain.filter(mutated);
    }

    private boolean isWhite(String path) {
        for (String pattern : WHITE_LIST) {
            if (PATH_MATCHER.match(pattern, path)) return true;
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }

    // 提前执行（越小越靠前）
    @Override
    public int getOrder() {
        return -100;
    }
}