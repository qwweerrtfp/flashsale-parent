package client;

import com.fasterxml.jackson.databind.ObjectMapper;

import constants.RedisConstants;
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
public class CacheClient {

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

    /** 读：统一逻辑过期（含防穿透、防击穿、异步刷新） */
    public <T, ID> T getLogical(
            String keyPrefix, String lockPrefix, ID id,
            Class<T> type, Function<ID, T> dbLoader,
            long logicTtl, TimeUnit unit
    ) {
        final String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);

        try {
            // 首次或被清空：需要一次加锁装填（互斥，防击穿）
            if (json == null) {
                final String lockKey = lockPrefix + id;
                RLock lock = redisson.getLock(lockKey);
                boolean locked = false;
                try {
                    locked = lock.tryLock(1, TimeUnit.SECONDS); // 看门狗自动续期
                    if (!locked) {
                        // 快失败：不阻塞，下一次很可能已被他人回填
                        return null;
                    }
                    // Double-Check
                    json = redis.opsForValue().get(key);
                    if (json != null) {
                        LogicalValue<T> wrap = mapper.readValue(json,
                                mapper.getTypeFactory().constructParametricType(LogicalValue.class, type));
                        return wrap.getData();
                    }
                    // DB 装填
                    T db = dbLoader.apply(id);
                    if (db == null) {
                        writeLogical(key, null, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES); // 负缓存（防穿透）
                        return null;
                    }
                    writeLogical(key, db, logicTtl, unit);
                    return db;
                } finally {
                    if (locked && redisson.getLock(lockKey).isHeldByCurrentThread()) lock.unlock();
                }
            }

            // 命中：判断逻辑过期
            LogicalValue<T> wrap = mapper.readValue(json,
                    mapper.getTypeFactory().constructParametricType(LogicalValue.class, type));
            long now = System.currentTimeMillis();
            if (wrap.getExpireAt() > now) {
                // 未过期，直接返回
                return wrap.getData();
            }

            // 已过期：返回旧值 + 异步刷新
            final String snapshot = json; // lambda 捕获需“有效 final”
            final String lockKey = lockPrefix + id;
            RLock lock = redisson.getLock(lockKey);
            if (lock.tryLock()) {
                REFRESH_POOL.execute(() -> {
                    try {
                        String latest = redis.opsForValue().get(key);
                        if (!Objects.equals(snapshot, latest)) return; // 已被别人刷新
                        T fresh = dbLoader.apply(id);
                        if (fresh == null) {
                            writeLogical(key, null, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                        } else {
                            writeLogical(key, fresh, logicTtl, unit);
                        }
                    } catch (Exception ignore) {
                    } finally {
                        if (lock.isHeldByCurrentThread()) lock.unlock();
                    }
                });
            }
            // 先返回旧值，保证可用性
            return wrap.getData();
        } catch (Exception e) {
            throw new RuntimeException("getLogical error, key=" + key, e);
        }
    }

    /** 写：事务提交后“刷新缓存”（不是删），避免旧值回填 */
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
        // 物理 TTL 可不设或设很长；逻辑过期由 expireAt 控制
        redis.opsForValue().set(key, mapper.writeValueAsString(v));
    }

    /** 逻辑过期包装结构 */
    public static class LogicalValue<T> {
        private T data;
        private long expireAt;
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public long getExpireAt() { return expireAt; }
        public void setExpireAt(long expireAt) { this.expireAt = expireAt; }
    }
}
