package com.ye94z.common.core.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 简单随机工具
 */
public final class MyUtils {

    private MyUtils(){}

    /** 数字+大小写字母 */
    public static final String ALL_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String NUM_CHARS = "0123456789";

    /** 生成指定长度的纯数字随机串 */
    public static String randomNumbers(int length) {
        if (length <= 0) return "";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(NUM_CHARS.charAt(r.nextInt(NUM_CHARS.length())));
        }
        return sb.toString();
    }

    /** 生成指定长度 [数字+大小写字母] 随机串 */
    public static String randomString(int length) {
        if (length <= 0) return "";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(ALL_CHARS.charAt(r.nextInt(ALL_CHARS.length())));
        }
        return sb.toString();
    }
}