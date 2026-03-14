package com.ye94z.common.core.exception;

/**
 * 统一错误码枚举。
 * 这里既保留 HTTP 常见错误码语义，也预留 1xxx 的业务错误码，
 * 方便“接口层错误”和“领域层错误”使用同一套枚举表达。
 */
public enum ErrorCode {

    SUCCESS(200, "OK"),

    // 通用客户端错误
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或身份已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 通用服务端错误
    INTERNAL_ERROR(500, "服务器开小差了，请稍后重试"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // 业务通用
    PARAMS_INVALID(1001, "参数不合法"),
    BUSINESS_CONFLICT(1002, "业务冲突"),
    STOCK_NOT_ENOUGH(1003, "库存不足"),
    DUPLICATE_OPERATION(1004, "重复操作"),
    ORDER_STATUS_ILLEGAL(1005, "订单状态不允许当前操作");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}
