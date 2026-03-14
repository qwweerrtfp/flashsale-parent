package com.ye94z.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye94z.common.core.pojo.Result;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2) // 优先级高于默认
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    /** 网关层单独维护一个 ObjectMapper，把异常统一序列化成 JSON 响应。 */
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String message;

        if (ex instanceof NotFoundException) {
            // Gateway 转发不到下游服务时，通常说明注册中心里没有实例。
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service unavailable";
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
        } else if (ex instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            message = ex.getMessage();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal server error";
        }

        // 对外始终保持统一响应结构，前端就不需要区分网关错误和业务服务错误。
        Result<?> result = Result.fail(String.valueOf(status.value()), message);

        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = mapper.writeValueAsBytes(result);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
