package com.ye94z.common.core.utils;

import java.util.regex.Pattern;

/**
 * 常用格式校验工具。
 * 目前只放了手机号、邮箱这种“跨模块都可能用到”的基础校验规则。
 */
public final class RegexUtils {

    private RegexUtils(){}

    /** 中国大陆手机：以 1 开头，第2位 3-9，总共11位 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 基础邮箱校验（简单版） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 校验手机号是否满足当前系统接受的格式。 */
    public static boolean isPhoneValid(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /** 语义化反向方法，方便业务代码直接写“invalid”判断。 */
    public static boolean isPhoneInvalid(String phone) {
        return !isPhoneValid(phone);
    }

    /** 基础邮箱格式校验。 */
    public static boolean isEmailValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /** 邮箱格式非法时返回 true。 */
    public static boolean isEmailInvalid(String email) {
        return !isEmailValid(email);
    }
}
