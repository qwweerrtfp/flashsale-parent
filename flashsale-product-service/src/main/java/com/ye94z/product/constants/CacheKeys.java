package com.ye94z.product.constants;

/**
 * 本服务使用到的 Redis Key 前缀
 * （值放这边是为了让 Product 模块“自描述”，不和其他服务混淆）
 */
public final class CacheKeys {
    private CacheKeys() {}

    /** 商品详情 缓存 key 前缀：cache:product:{id} */
    public static final String CACHE_PRODUCT_KEY = "cache:product:";

    /** 商品详情 重建互斥锁 前缀：lock:product:{id} */
    public static final String LOCK_PRODUCT_KEY  = "lock:product:";
}