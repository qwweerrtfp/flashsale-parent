package com.ye94z.product.client;

import com.fasterxml.jackson.databind.ObjectMapper;


import com.ye94z.common.core.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 统一缓存组件（逻辑过期版：stale-while-revalidate）
 * 读：防穿透（空值占位）、防击穿（互斥锁+双检）、热点逻辑过期（返回旧值+异步刷新）
 * 写：afterCommit 后刷新缓存，避免旧值回填窗口
 */
@Component
@Slf4j
public class CacheClient {

    /** 异步刷新缓存的线程池，容量控制得比较小，避免缓存重建本身反过来压垮服务。 */
    private static final ExecutorService REFRESH_POOL = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            r -> { Thread t = new Thread(r, "cache-refresh"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ObjectMapper mapper;

    public CacheClient(StringRedisTemplate redis, RedissonClient redisson, ObjectMapper mapper) {
        this.redis = redis;
        this.redisson = redisson;
        this.mapper = mapper;
    }

    /**
     * 统一逻辑过期读取逻辑。
     * 这个方法把防穿透、防击穿和“过期后返回旧值 + 异步刷新”这几件事封装到了一起。
     */
    public <T, ID> T getLogical(
            String keyPrefix, String lockPrefix, ID id,
            Class<T> type, Function<ID, T> dbLoader,
            long logicTtl, TimeUnit unit
    ) {
        final String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);

        try {
            // 首次访问或缓存被清空时，需要由一个线程负责回源并重建缓存。
            if (json == null) {
                final String lockKey = lockPrefix + id;
                RLock lock = redisson.getLock(lockKey);
                boolean locked = false;
                try {
                    locked = lock.tryLock(1, TimeUnit.SECONDS); // 看门狗自动续期
                    if (!locked) {
                        // 抢不到锁就快速返回，不在热点场景里阻塞业务线程。
                        return null;
                    }
                    // 双重检查，避免等拿到锁时别人已经写好了缓存。
                    json = redis.opsForValue().get(key);
                    if (json != null) {
                        LogicalValue<T> wrap = mapper.readValue(json,
                                mapper.getTypeFactory().constructParametricType(LogicalValue.class, type));
                        return wrap.getData();
                    }
                    // 确认缓存仍为空后，才真正回源数据库。
                    T db = dbLoader.apply(id);
                    if (db == null) {
                        // 对不存在的数据写一个短 TTL 的空值包装，防止持续穿透数据库。
                        writeLogical(key, null, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return null;
                    }
                    writeLogical(key, db, logicTtl, unit);
                    return db;
                } finally {
                    if (locked && redisson.getLock(lockKey).isHeldByCurrentThread()) lock.unlock();
                }
            }

            // 命中缓存后，通过 expireAt 判断逻辑是否过期，而不是依赖 Redis 物理 TTL。
            LogicalValue<T> wrap = mapper.readValue(json,
                    mapper.getTypeFactory().constructParametricType(LogicalValue.class, type));
            long now = System.currentTimeMillis();
            if (wrap.getExpireAt() > now) {
                // 逻辑上仍有效，直接返回。
                return wrap.getData();
            }

            // 过期后优先返回旧值保证可用性，再异步刷新缓存。
            final String snapshot = json;
            final String lockKey = lockPrefix + id;

            REFRESH_POOL.execute(() -> {
                RLock lock = redisson.getLock(lockKey);
                boolean locked = false;
                try {
                    // 不等待锁，抢到才刷新，避免多个线程同时回源数据库。
                    locked = lock.tryLock(0, TimeUnit.SECONDS);
                    if (!locked) return;

                    String latest = redis.opsForValue().get(key);
                    if (!Objects.equals(snapshot, latest)) return; // 已经被别的线程刷新过了

                    T fresh = dbLoader.apply(id);
                    writeLogical(key, fresh, logicTtl, unit); // fresh==null 时会写入空值包装
                } catch (Exception e) {
                    log.warn("async refresh error, key={}", key, e);
                } finally {
                    if (locked && lock.isHeldByCurrentThread()) lock.unlock();
                }
            });
            // 最终优先把旧值返回给调用方，避免缓存刷新拖慢用户请求。
            return wrap.getData();
        } catch (Exception e) {
            throw new RuntimeException("getLogical error, key=" + key, e);
        }
    }

    /**
     * 在事务提交后刷新缓存。
     * 这里选择直接回写新值，而不是简单删除缓存，目的是减少热点 key 的冷启动成本。
     */
    public <T> void refreshAfterCommit(String fullKey, Supplier<T> loader, long logicTtl, TimeUnit unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writeNow(fullKey, loader, logicTtl, unit);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                writeNow(fullKey, loader, logicTtl, unit);
            }
        });
    }

    /* ---------- 内部工具 ---------- */

    private <T> void writeNow(String key, Supplier<T> loader, long ttl, TimeUnit unit) {
        try {
            T fresh = loader.get();
            if (fresh == null) {
                writeLogical(key, null, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            } else {
                writeLogical(key, fresh, ttl, unit);
            }
        } catch (Exception ignore) { /* 可加轻量重试/告警 */ }
    }

    private <T> void writeLogical(String key, T data, long ttl, TimeUnit unit) throws Exception {
        LogicalValue<T> v = new LogicalValue<>();
        v.setData(data);
        v.setExpireAt(Instant.now().plusMillis(unit.toMillis(ttl)).toEpochMilli());
        // 物理 TTL 不参与本方案的“是否可用”判断，expireAt 才是真正的逻辑过期时间。
        redis.opsForValue().set(key, mapper.writeValueAsString(v));
    }

    /** 逻辑过期包装结构：真正的数据和逻辑过期时间一起保存。 */
    public static class LogicalValue<T> {
        private T data;
        private long expireAt;
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public long getExpireAt() { return expireAt; }
        public void setExpireAt(long expireAt) { this.expireAt = expireAt; }
    }
}
