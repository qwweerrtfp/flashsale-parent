package com.ye94z.common.core.constants;

/**
 * Redis Key 前缀 & TTL（单位见注释）
 * 说明：尽量只放“通用型”常量，业务私有的放对应服务里。
 */
public final class RedisConstants {

    private RedisConstants() {}

    /* 登录验证码 */
    public static final String LOGIN_CODE_KEY = "login:code:"; // 后接手机号
    public static final long LOGIN_CODE_TTL = 2L;              // 分钟

    /* 缓存空值（防穿透）统一TTL（分钟） */
    public static final long CACHE_NULL_TTL = 2L;

    /* 店铺缓存（兼容你旧代码；新项目的商品可使用 item 前缀自定义） */
    public static final String CACHE_SHOP_KEY = "cache:shop:"; // 后接id
    public static final String LOCK_SHOP_KEY  = "lock:shop:";  // 后接id

    /* 可留作新商品缓存前缀（如采用）： */
    // public static final String CACHE_ITEM_KEY = "cache:item:";
    // public static final String LOCK_ITEM_KEY  = "lock:item:";
}