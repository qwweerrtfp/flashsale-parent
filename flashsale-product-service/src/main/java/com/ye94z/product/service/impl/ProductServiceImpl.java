package com.ye94z.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye94z.common.core.dto.ProductDTO;
import com.ye94z.common.core.dto.Result;
import com.ye94z.product.client.CacheClient;
import com.ye94z.product.constants.CacheKeys;
import com.ye94z.product.entity.FlashProduct;
import com.ye94z.product.mapper.FlashProductMapper;
import com.ye94z.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.ye94z.product.constants.CacheKeys.STOCK_PREFIX;
import static com.ye94z.product.constants.CacheKeys.USER_BUY_HASH;

/**
 * 产品领域服务
 * - getProduct：逻辑过期缓存（热点0DB）
 * - gatePurchase：基于缓存快照做业务校验，Lua 原子扣库存 & 累计（热点0DB）
 * - restoreStock：取消/超时回补（DB 对齐 + 提交后修复 Redis & 释放限购 + 刷新缓存）
 * - deductStock：下单成功后将 DB 与 Redis 对齐（DB 扣减 + 刷新缓存）
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final FlashProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    private final ObjectMapper objectMapper;

    /** 逻辑缓存前缀与锁前缀（与 CacheClient 配合） */
    private static final String CACHE_PRODUCT_KEY = CacheKeys.CACHE_PRODUCT_KEY; // "cache:product:"
    private static final String LOCK_PRODUCT_KEY  = CacheKeys.LOCK_PRODUCT_KEY;  // "lock:product:"

    /** 从类路径加载 Lua（ARGV: productId, userId, qty, limitPerUser） */
    private static final DefaultRedisScript<Long> FLASH_GATE_SCRIPT;
    static {
        FLASH_GATE_SCRIPT = new DefaultRedisScript<>();
        FLASH_GATE_SCRIPT.setLocation(new ClassPathResource("lua/flash_gate.lua"));
        FLASH_GATE_SCRIPT.setResultType(Long.class);
    }

    // -------------------- 对外查询 --------------------

    @Override
    public Result<ProductDTO> getProduct(Long id) {
        if (id == null || id <= 0) return Result.fail("参数错误：id 非法");

        // 逻辑过期缓存：热点基本 0 次 DB 读；冷启动或缓存失效时由 CacheClient 回源一次 DB 并回写
        FlashProduct p = cacheClient.getLogical(
                CACHE_PRODUCT_KEY, LOCK_PRODUCT_KEY,
                id,
                FlashProduct.class,
                productMapper::findById,
                30, TimeUnit.MINUTES
        );
        if (p == null) return Result.fail("商品不存在");

        ProductDTO dto = new ProductDTO();
        dto.setId(p.getId());
        dto.setTitle(p.getTitle());
        dto.setFlashPriceCents(p.getFlashPriceCents());
        dto.setOriginPriceCents(p.getOriginPriceCents());
        dto.setLimitPerUser(p.getLimitPerUser());
        dto.setStartTime(p.getStartTime());
        dto.setEndTime(p.getEndTime());
        dto.setStatus(p.getStatus());
        return Result.ok(dto);
    }

    // -------------------- 下单闸口（热点0DB） --------------------

    @Override
    public Result<Void> gatePurchase(Long productId, Long userId, Integer quantity) {
        if (productId == null || userId == null) return Result.fail("参数错误");
        final int qty = (quantity == null || quantity <= 0) ? 1 : quantity;

        // 1) 用缓存快照做业务校验（状态/时间/限购），避免热读打DB
        FlashProduct p = cacheClient.getLogical(
                CACHE_PRODUCT_KEY, LOCK_PRODUCT_KEY,
                productId,
                FlashProduct.class,
                productMapper::findById,
                30, TimeUnit.MINUTES
        );
        if (p == null) return Result.fail("商品不存在");
        if (p.getStatus() == null || p.getStatus() != 2) return Result.fail("商品不可售"); // 2=ONLINE

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(p.getStartTime())) return Result.fail("活动未开始");
        if (now.isAfter(p.getEndTime()))  return Result.fail("活动已结束");

        final int limit = (p.getLimitPerUser() == null || p.getLimitPerUser() <= 0) ? 1 : p.getLimitPerUser();

        // 2) 执行 Lua：库存 >= qty && (累计+qty) <= limit -> 原子扣库存 & 累计
        Long ret = stringRedisTemplate.execute(
                FLASH_GATE_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),     // ARGV[1]
                String.valueOf(userId),        // ARGV[2]
                String.valueOf(qty),           // ARGV[3]
                String.valueOf(limit)          // ARGV[4]
        );
        int code = (ret == null) ? -1 : ret.intValue();
        if (code == 0) return Result.ok();

        return switch (code) {
            case 1 -> Result.fail("库存不足");
            case 2 -> Result.fail("超过每人限购");
            default -> Result.fail("下单校验失败");
        };
    }

    // -------------------- 取消/超时回补（DB对齐 + 提交后修复 Redis & 限购 + 刷新缓存） --------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> restoreStock(Long productId, Integer quantity, Long userId) {
        if (productId == null || userId == null || quantity == null || quantity <= 0) {
            return Result.fail("参数错误");
        }

        // DB 回补（+stock, -sold）
        int n = productMapper.incrStockDecrSold(productId, quantity);
        if (n <= 0) return Result.fail("回补失败或已被处理");

        // 事务提交后，再修复 Redis 实时数据 + 释放限购额度
        final String stockKey   = STOCK_PREFIX  + productId;   // String
        final String userBuyKey = USER_BUY_HASH + productId;   // Hash, field=userId

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // 回补库存到 Redis
                stringRedisTemplate.opsForValue().increment(stockKey, quantity.longValue());
                // 释放用户累计（避免多次取消导致负数：可再加 max(0, 结果) 的保护，这里保持简单）
                stringRedisTemplate.opsForHash().increment(userBuyKey, userId.toString(), -quantity.longValue());
            }
        });

        // 刷新产品缓存，避免旧值回填
        cacheClient.refreshAfterCommit(
                CACHE_PRODUCT_KEY + productId,
                () -> productMapper.findById(productId),
                30, TimeUnit.MINUTES
        );

        return Result.ok();
    }

    @Override
    @Transactional
    public Result<Void> onSale(Long id) {
        FlashProduct p = productMapper.findById(id);
        if (p == null) return Result.fail("商品不存在");
        Integer stock = p.getStock();
        if (stock == null || stock <= 0) return Result.fail("库存不足");

        // 避免重复上架
        int i = productMapper.onSale(id);
        if (i <= 0) return Result.fail("上架失败或已上架");

        cacheClient.refreshAfterCommit(
                CACHE_PRODUCT_KEY + id,
                () -> productMapper.findById(id),
                30, TimeUnit.MINUTES
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                stringRedisTemplate.opsForValue().setIfAbsent(STOCK_PREFIX + id, String.valueOf(stock));
            }
        });
        return Result.ok();
    }

    // -------------------- 下单成功后 DB 扣减（与 Redis 对齐） --------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deductStock(Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            return Result.fail("参数错误");
        }

        // DB 扣减（-stock, +sold），与 Redis 侧 Lua 的扣减保持一致
        int n = productMapper.decrStockIncrSold(productId, quantity);
        if (n <= 0) return Result.fail("库存不足或已被抢完");

        // 刷新产品缓存，避免旧值回填（Redis 实时库存/累计由 Lua 已正确维护，这里只刷逻辑缓存）
        cacheClient.refreshAfterCommit(
                CACHE_PRODUCT_KEY + productId,
                () -> productMapper.findById(productId),
                30, TimeUnit.MINUTES
        );

        return Result.ok();
    }
}