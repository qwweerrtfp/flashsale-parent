package com.ye94z.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye94z.common.core.constants.RedisConstants;
import com.ye94z.common.core.dto.ProductDTO;
import com.ye94z.common.core.dto.Result;
import com.ye94z.product.client.CacheClient;
import com.ye94z.product.entity.FlashProduct;
import com.ye94z.product.mapper.FlashProductMapper;
import com.ye94z.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 产品领域服务
 * - getProduct：逻辑过期缓存（热点0DB）
 * - gatePurchase：基于缓存快照做业务校验，Lua 原子扣库存 & 累计（热点0DB）
 * - restoreStock：取消/超时回补（DB 对齐 + 提交后修复 Redis & 释放限购 + 刷新缓存）
 * - deductStock：下单成功后将 DB 与 Redis 对齐（DB 扣减 + 刷新缓存）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final FlashProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    private final ObjectMapper objectMapper;

    /** 逻辑缓存前缀与锁前缀（与 CacheClient 配合） */
    private static final String CACHE_PRODUCT_KEY = RedisConstants.CACHE_PRODUCT_KEY; // "cache:product:"
    private static final String LOCK_PRODUCT_KEY  = RedisConstants.LOCK_PRODUCT_KEY;  // "lock:product:"

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
        dto.setStock(p.getStock());
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> update(ProductDTO productDTO) {
        if (productDTO == null || productDTO.getId() == null) {
            return Result.fail("参数错误");
        }
        int i = productMapper.update(productDTO);
        if(i == 0) return Result.fail("更新失败");
        cacheClient.refreshAfterCommit(
                CACHE_PRODUCT_KEY + productDTO.getId(),
                () -> productMapper.findById(productDTO.getId()),
                30, TimeUnit.MINUTES
        );
        return Result.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> onSale(Long id) {
        if(id == null || id <= 0) return Result.fail("参数错误");
        Result<ProductDTO> productDTOResult = getProduct(id);
        if(!productDTOResult.isSuccess() || productDTOResult.getData() == null){
            return Result.fail("商品不存在");
        }
        Integer stock = productDTOResult.getData().getStock();
        if(stock == null) return Result.fail("上架失败, 商品库存为 null");

        ProductDTO p = new ProductDTO();
        p.setId(id);
        p.setStatus(2);

        Result<Void> update = update(p);
        if(!update.isSuccess()) return Result.fail("上架失败");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.STOCK_PREFIX + id, String.valueOf(stock));
                } catch (Exception e) {
                    log.warn("[onSale] 缓存库存失败", e);
                }
            }
        });
        return Result.ok();
    }
}