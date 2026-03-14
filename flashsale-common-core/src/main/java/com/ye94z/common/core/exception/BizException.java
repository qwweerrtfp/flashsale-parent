package com.ye94z.common.core.exception;

/**
 * 业务异常基类。
 * 运行时异常可以避免在 Service 层四处显式 throws，
 * 同时又能保留独立的业务错误码，方便网关或控制器统一转成标准响应。
 */
public class BizException extends RuntimeException {

    /** 面向前端或上层调用方的业务错误码。 */
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }
}
