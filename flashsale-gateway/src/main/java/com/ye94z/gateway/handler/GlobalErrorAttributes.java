package com.ye94z.gateway.handler;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GlobalErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request,
                                                  ErrorAttributeOptions options) {
        Throwable error = getError(request);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Internal Server Error";

        if (error instanceof ResponseStatusException ex) {
            status = HttpStatus.valueOf(ex.getStatusCode().value());
            message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        } else if (error instanceof IllegalArgumentException ex) {
            status = HttpStatus.BAD_REQUEST;
            message = ex.getMessage();
        } else if (error != null && error.getMessage() != null) {
            message = error.getMessage();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", status.value()); // 可与 status 保持一致，便于前端统一解析
        body.put("message", message);
        body.put("path", request.path());
        body.put("requestId", request.exchange().getRequest().getId());
        return body;
    }
}