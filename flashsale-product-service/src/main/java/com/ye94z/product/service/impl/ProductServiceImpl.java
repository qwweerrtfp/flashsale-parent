package com.ye94z.product.service.impl;

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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 说明：
 * 1) getProduct 使用逻辑过期缓存（CacheClient），key 形如 "cache:product:{id}"
 * 2) gatePurchase 先做 DB 时间/状态校验，再执行 Lua（库存/限购累计/原子扣减）
 * 3) restoreStock 回补库存（DB + Redis），不回退用户购买累计（避免被刷）
 *
 * 依赖前提：
 * - resources/lua/flash_gate.lua 已放置（脚本内部拼 key：flash:stock:{id} / flash:buy:{id}:{userId}）
 * - FlashProductMapper 至少包含：findById、incrStock(productId, qty)（若没有，请补充）
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final FlashProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    /** 逻辑缓存前缀与锁前缀（与 CacheClient 配合） */
    private static final String CACHE_PRODUCT_KEY = "cache:product:";
    private static final String LOCK_PRODUCT_KEY  = "lock:product:";

    /** 从类路径加载 Lua（支持多件限购：ARGV = productId, userId, qty, limitPerUser） */
    private static final DefaultRedisScript<Long> FLASH_GATE_SCRIPT;
    static {
        FLASH_GATE_SCRIPT = new DefaultRedisScript<>();
        FLASH_GATE_SCRIPT.setLocation(new ClassPathResource("lua/flash_gate.lua"));
        FLASH_GATE_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result<Boolean> gatePurchase(Long productId, Long userId, Integer quantity) {
        if (productId == null || userId == null) {
            return Result.fail("参数错误");
        }
        int qty = (quantity == null || quantity <= 0) ? 1 : quantity;

        // 1) 基础校验（DB）：在线/时间窗口
        FlashProduct p = productMapper.findById(productId);
        if (p == null || p.getStatus() == null || p.getStatus() != 2) { // 2 = ONLINE
            return Result.fail("商品不可售");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(p.getStartTime())) return Result.fail("活动未开始");
        if (now.isAfter(p.getEndTime()))   return Result.fail("活动已结束");

        int limit = (p.getLimitPerUser() == null || p.getLimitPerUser() <= 0) ? 1 : p.getLimitPerUser();

        // 2) 执行 Lua：库存 >= qty 且 (累计+qty) <= limit -> 原子扣库存 & 累计购买数
        Long ret = stringRedisTemplate.execute(
                FLASH_GATE_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),    // ARGV[1]
                String.valueOf(userId),       // ARGV[2]
                String.valueOf(qty),          // ARGV[3]
                String.valueOf(limit)         // ARGV[4]
        );
        int code = ret == null ? -1 : ret.intValue();
        if (code == 0) return Result.ok(true);
        return switch (code) {
            case 1 -> Result.fail("库存不足");
            case 2 -> Result.fail("超过每人限购");
            default -> Result.fail("下单校验失败");
        };
    }

    @Override
    public Result<ProductDTO> getProduct(Long id) {
        if (id == null || id <= 0) {
            return Result.fail("参数错误：id 非法");
        }
        FlashProduct product = cacheClient.getLogical(
                CACHE_PRODUCT_KEY, LOCK_PRODUCT_KEY,
                id,
                FlashProduct.class,
                productMapper::findById,
                30, TimeUnit.MINUTES
        );
        if (product == null) return Result.fail("商品不存在");

        // 转成对外 DTO
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setTitle(product.getTitle());
        dto.setFlashPriceCents(product.getFlashPriceCents());
        dto.setOriginPriceCents(product.getOriginPriceCents());
        dto.setLimitPerUser(product.getLimitPerUser());
        dto.setStartTime(product.getStartTime());
        dto.setEndTime(product.getEndTime());
        dto.setStatus(product.getStatus());
        return Result.ok(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> restoreStock(Long productId, Integer quantity, Long userId) {
        if (productId == null || userId == null || quantity == null || quantity <= 0) {
            return Result.fail("参数错误");
        }

        int n = productMapper.incrStockDecrSold(productId, quantity);
        if (n <= 0) {
            return Result.fail("回补失败或已被处理");
        }

        final String stockKey = "flash:stock:" + productId;
        final String userBuyKey = "flash:buy:" + productId; // Lua 中对人限购用的计数容器（推荐用 HASH: HINCRBY userId）

        // 提交后再改 Redis & 刷新缓存
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // 库存加回
                stringRedisTemplate.opsForValue().increment(stockKey, quantity.longValue());
                // 用户购买数减回（释放限购额度）
                stringRedisTemplate.opsForHash().increment(userBuyKey, userId.toString(), -quantity.longValue());
            }
        });

        cacheClient.refreshAfterCommit(
                CacheKeys.CACHE_PRODUCT_KEY + productId,
                () -> productMapper.findById(productId),
                30, TimeUnit.MINUTES
        );

        return Result.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> deductStock(Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            return Result.fail("参数错误");
        }

        // DB 扣减库存（可选：同时增加已售）
        int n = productMapper.decrStockIncrSold(productId, quantity);
        if (n <= 0) {
            // 说明 DB 中库存不足或已被并发扣完
            return Result.fail("库存不足或已被抢完");
        }

        // 提交后刷新逻辑缓存，避免旧值回填（不动 Redis 计数，Lua 已扣过）
        cacheClient.refreshAfterCommit(
                CacheKeys.CACHE_PRODUCT_KEY + productId,
                () -> productMapper.findById(productId),
                30, TimeUnit.MINUTES
        );

        return Result.ok();
    }
}