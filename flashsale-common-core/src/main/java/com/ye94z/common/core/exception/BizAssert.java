package com.ye94z.common.core.exception;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 轻量断言工具：不依赖第三方，抛 BizException
 */
public final class BizAssert {

    private BizAssert() {}

    public static void isTrue(boolean expression, ErrorCode code, String message) {
        if (!expression) {
            throw new BizException(code, message);
        }
    }

    public static void isTrue(boolean expression, ErrorCode code) {
        if (!expression) {
            throw new BizException(code);
        }
    }

    public static void notNull(Object obj, ErrorCode code, String message) {
        if (obj == null) {
            throw new BizException(code, message);
        }
    }

    public static void notNull(Object obj, ErrorCode code) {
        if (obj == null) {
            throw new BizException(code);
        }
    }

    public static void isNull(Object obj, ErrorCode code, String message) {
        if (obj != null) {
            throw new BizException(code, message);
        }
    }

    public static void equals(Object a, Object b, ErrorCode code, String message) {
        if (!Objects.equals(a, b)) {
            throw new BizException(code, message);
        }
    }

    public static <T> T orThrow(T value, Supplier<BizException> exSupplier) {
        if (value == null) throw exSupplier.get();
        return value;
    }
}