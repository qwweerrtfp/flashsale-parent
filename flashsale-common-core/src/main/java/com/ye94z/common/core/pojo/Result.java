package com.ye94z.common.core.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 项目统一响应模型。
 * 约定很简单：success 表示本次调用是否成功，data 承载成功结果，
 * errorMsg / errorCode 承载失败时给调用方看的错误信息。
 *
 * 这里保持零业务依赖，方便在 gateway、user、order、payment 等模块间统一复用。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> implements Serializable {
    /** 是否成功。前端通常优先根据这个字段判断本次调用是否需要走异常分支。 */
    private boolean success;
    /** 成功时返回的数据载荷。失败时一般为 null。 */
    private T data;
    /** 失败时给调用方展示或记录日志的消息。 */
    private String errorMsg;
    /** 可选错误码，方便前端做更细粒度的错误分流。 */
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
