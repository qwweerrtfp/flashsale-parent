package com.ye94z.common.core.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一返回结果（零依赖版本）
 * 用法：
 *   return Result.ok(data);
 *   return Result.fail("错误信息");
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> implements Serializable {
    private boolean success;
    private T data;
    private String errorMsg;
    private String errorCode;

    public static <T> Result<T> ok() {
        return new Result<>(true, null, null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, data, null, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(false, null, message, null);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(false, null, message, code);
    }
}