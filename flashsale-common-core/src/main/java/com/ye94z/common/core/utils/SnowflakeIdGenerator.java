package com.ye94z.common.core.utils;

/**
 * 经典 Snowflake 64 位 ID 生成器。
 * 订单服务使用它生成分布式有序 ID，避免依赖数据库自增主键。
 *
 * 位结构：
 * 1 bit  符号位
 * 41 bit 时间戳差值
 * 5 bit  数据中心
 * 5 bit  机器编号
 * 12 bit 同毫秒内序列号
 */
public class SnowflakeIdGenerator {

    /** 自定义起始纪元，缩短时间戳占用位数。 */
    private static final long EPOCH = 1704067200000L;

    private static final long DATACENTER_BITS = 5L;
    private static final long WORKER_BITS = 5L;
    private static final long SEQ_BITS = 12L;

    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS); // 31
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);         // 31
    private static final long SEQ_MASK = ~(-1L << SEQ_BITS);                 // 4095

    private static final long WORKER_SHIFT = SEQ_BITS;
    private static final long DATACENTER_SHIFT = SEQ_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQ_BITS + WORKER_BITS + DATACENTER_BITS;

    private final long datacenterId;
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId out of range [0, " + MAX_DATACENTER_ID + "]");
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId out of range [0, " + MAX_WORKER_ID + "]");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 获取下一个 ID。
     * synchronized 的目的不是为了高性能，而是先确保生成逻辑的正确性：
     * 同一实例内必须串行维护 lastTimestamp 和 sequence。
     */
    public synchronized long nextId() {
        long ts = currentTime();

        if (ts < lastTimestamp) {
            // 出现时钟回拨时，先尝试等待本地时间追平。
            // 这是示例项目里最容易理解的策略，生产中也可以改成报错或借位序列。
            long offset = lastTimestamp - ts;
            try {
                Thread.sleep(offset);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ts = currentTime();
            if (ts < lastTimestamp) {
                // 如果等待后仍落后，至少保证本实例生成的 ID 不回退。
                ts = lastTimestamp;
            }
        }

        if (ts == lastTimestamp) {
            // 同一毫秒内递增序列号，保证 ID 不冲突。
            sequence = (sequence + 1) & SEQ_MASK;
            if (sequence == 0) {
                // 序列耗尽就等待到下一毫秒，继续生成。
                ts = waitNextMillis(lastTimestamp);
            }
        } else {
            // 跨毫秒后从 0 重新开始计数。
            sequence = 0L;
        }

        lastTimestamp = ts;

        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTime();
        while (ts <= lastTs) {
            // 自旋直到系统时钟进入下一毫秒。
            ts = currentTime();
        }
        return ts;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
