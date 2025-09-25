package com.ye94z.common.core.utils;

import java.util.regex.Pattern;

/**
 * 常用正则校验
 */
public final class RegexUtils {

    private RegexUtils(){}

    /** 中国大陆手机：以 1 开头，第2位 3-9，总共11位 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 基础邮箱校验（简单版） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static boolean isPhoneValid(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    public static boolean isPhoneInvalid(String phone) {
        return !isPhoneValid(phone);
    }

    public static boolean isEmailValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isEmailInvalid(String email) {
        return !isEmailValid(email);
    }
}