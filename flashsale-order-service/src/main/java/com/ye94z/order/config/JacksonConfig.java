package com.ye94z.order.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.text.SimpleDateFormat;

@AutoConfiguration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 忽略 null 字段，减少消息和接口响应中的冗余内容。
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // 统一日期格式，降低跨服务序列化差异。
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        // Long 转 String，兼容前端对大整数的精度限制。
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        mapper.registerModule(module);
        return mapper;
    }
}
