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

    // 白名单路径不会触发 JWT 校验，一般放登录、文档、健康检查接口。
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
        // 先判断是否命中白名单，命中则直接放行，避免把登录接口也拦住。
        if (isWhite(path)) {
            return chain.filter(exchange);
        }

        // 这里约定前端把 token 放在 Authorization 头中。
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(auth)) {
            return unauthorized(exchange.getResponse(), "Missing Authorization");
        }

        String token = auth;
        if (auth.toLowerCase().startsWith("bearer ")) {
            // 兼容标准 Bearer Token 形式。
            token = auth.substring(7);
        }

        Claims claims = JwtUtils.parseToken(token);
        if (claims == null) {
            return unauthorized(exchange.getResponse(), "Invalid or expired token");
        }

        // 从 claims 中取出用户上下文，后续通过网关注入到下游请求头。
        Long userId = claims.get("userId", Long.class);
        String nickName = claims.get("nickName", String.class);
        String icon = claims.get("icon", String.class);

        // 透传到下游时使用自定义头，减少被标准鉴权组件误处理的概率。
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Nick");
                    h.remove("X-User-Icon");
                    // 先清理再写入，避免多次经过网关或重试时带着旧值。
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

    // 越小越先执行，保证大部分业务过滤器看到的都是已经注入用户上下文的请求。
    @Override
    public int getOrder() {
        return -100;
    }
}
