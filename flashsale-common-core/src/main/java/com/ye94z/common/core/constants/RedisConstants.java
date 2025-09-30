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

    public static final String ORDER_PERSISTED_KEY = "order:persisted:";

    /** 商品详情 缓存 key 前缀：cache:product:{id} */
    public static final String CACHE_PRODUCT_KEY = "cache:product:";

    /** 商品详情 重建互斥锁 前缀：lock:product:{id} */
    public static final String LOCK_PRODUCT_KEY  = "lock:product:";

    /** Redis 实时库存与用户累计的 key 前缀（Lua 里也用这两个） */
    public static final String STOCK_PREFIX   = "flash:stock:"; // String -> INCRBY/DECRBY
    public static final String USER_BUY_HASH  = "flash:buy:";   // Hash    -> HINCRBY field=userId
}