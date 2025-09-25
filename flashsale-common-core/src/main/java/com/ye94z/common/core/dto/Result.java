package com.ye94z.common.core.dto;

import java.io.Serializable;

/**
 * 统一返回结果（零依赖版本）
 * 用法：
 *   return Result.ok(data);
 *   return Result.fail("错误信息");
 */
public class Result<T> implements Serializable {
    private boolean success;
    private T data;
    private String errorMsg;
    private String errorCode;

    public Result() {}

    private Result(boolean success, T data, String errorMsg, String errorCode) {
        this.success = success;
        this.data = data;
        this.errorMsg = errorMsg;
        this.errorCode = errorCode;
    }

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

    // getters & setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}