package com.ye94z.common.core.exception;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 轻量级业务断言工具。
 * 和普通的 Objects.requireNonNull 不同，这里抛出的是 BizException，
 * 方便上层统一转成带业务语义的 Result。
 */
public final class BizAssert {

    private BizAssert() {}

    /** 断言表达式必须为 true，否则按指定错误码抛出业务异常。 */
    public static void isTrue(boolean expression, ErrorCode code, String message) {
        if (!expression) {
            throw new BizException(code, message);
        }
    }

    /** 断言表达式必须为 true，异常消息直接使用错误码默认文案。 */
    public static void isTrue(boolean expression, ErrorCode code) {
        if (!expression) {
            throw new BizException(code);
        }
    }

    /** 断言对象不能为空，否则抛出带自定义消息的业务异常。 */
    public static void notNull(Object obj, ErrorCode code, String message) {
        if (obj == null) {
            throw new BizException(code, message);
        }
    }

    /** 断言对象不能为空，否则抛出错误码默认消息。 */
    public static void notNull(Object obj, ErrorCode code) {
        if (obj == null) {
            throw new BizException(code);
        }
    }

    /** 断言对象必须为空，常用于“重复创建”“重复提交”这类校验。 */
    public static void isNull(Object obj, ErrorCode code, String message) {
        if (obj != null) {
            throw new BizException(code, message);
        }
    }

    /** 断言两个对象必须相等，否则抛出业务异常。 */
    public static void equals(Object a, Object b, ErrorCode code, String message) {
        if (!Objects.equals(a, b)) {
            throw new BizException(code, message);
        }
    }

    /** 对可能为 null 的值做包装式断言，适合链式取值场景。 */
    public static <T> T orThrow(T value, Supplier<BizException> exSupplier) {
        if (value == null) throw exSupplier.get();
        return value;
    }
}
