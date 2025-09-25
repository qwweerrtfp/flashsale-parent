package com.ye94z.common.core.utils;

/**
 * Twitter Snowflake 64-bit ID 生成器
 * 结构：1bit 符号位(0) + 41bit 时间戳 + 5bit 数据中心 + 5bit 机器 + 12bit 序列
 * 单机可将 datacenterId/workerId 设为0；多实例建议通过配置注入不同ID。
 */
public class SnowflakeIdGenerator {

    // 起始纪元（自定义）：2024-01-01 00:00:00 UTC
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

    /** 线程安全：获取下一个ID */
    public synchronized long nextId() {
        long ts = currentTime();

        if (ts < lastTimestamp) {
            // 时钟回拨：简单处理——等待到 lastTimestamp
            long offset = lastTimestamp - ts;
            try {
                Thread.sleep(offset);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ts = currentTime();
            if (ts < lastTimestamp) {
                // 仍落后，强行推进
                ts = lastTimestamp;
            }
        }

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & SEQ_MASK;
            if (sequence == 0) {
                // 同毫秒内序列溢出，阻塞到下一毫秒
                ts = waitNextMillis(lastTimestamp);
            }
        } else {
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
            ts = currentTime();
        }
        return ts;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}