package com.ye94z.product.service.impl;

import com.ye94z.common.core.constants.RedisConstants;
import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
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

    /** 商品表访问层。 */
    private final FlashProductMapper productMapper;
    /** Redis 主要承载库存、限购累计和缓存。 */
    private final StringRedisTemplate stringRedisTemplate;
    /** 统一的逻辑过期缓存组件。 */
    private final CacheClient cacheClient;

    /** 逻辑缓存 key 前缀与互斥锁前缀。 */
    private static final String CACHE_PRODUCT_KEY = RedisConstants.CACHE_PRODUCT_KEY; // "cache:product:"
    private static final String LOCK_PRODUCT_KEY  = RedisConstants.LOCK_PRODUCT_KEY;  // "lock:product:"

    /** 从类路径加载 Lua 脚本，把高并发下的原子逻辑放到 Redis 侧执行。 */
    private static final DefaultRedisScript<Long> FLASH_GATE_SCRIPT;
    private static final DefaultRedisScript<Long> RESTORE_STOCK_SCRIPT;
    static {
        FLASH_GATE_SCRIPT = new DefaultRedisScript<>();
        FLASH_GATE_SCRIPT.setLocation(new ClassPathResource("lua/flash_gate.lua"));
        FLASH_GATE_SCRIPT.setResultType(Long.class);

        RESTORE_STOCK_SCRIPT = new DefaultRedisScript<>();
        RESTORE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/restore_stock.lua"));
        RESTORE_STOCK_SCRIPT.setResultType(Long.class);
    }

    // -------------------- 对外查询 --------------------

    @Override
    public Result<ProductDTO> getProduct(Long id) {
        if (id == null || id <= 0) return Result.fail("参数错误：id 非法");

        // 热点商品优先走逻辑过期缓存，减少高并发下数据库被打穿的概率。
        FlashProduct p = cacheClient.getLogical(
                CACHE_PRODUCT_KEY, LOCK_PRODUCT_KEY,
                id,
                FlashProduct.class,
                productMapper::findById,
                30, TimeUnit.MINUTES
        );
        if (p == null) return Result.fail("商品不存在");

        // 显式转换为 DTO，避免把数据库实体直接暴露到服务边界之外。
        ProductDTO dto = new ProductDTO();
        dto.setId(p.getId());
        dto.setInitStock(p.getStock());
        dto.setTitle(p.getTitle());
        dto.setFlashPriceCents(p.getFlashPriceCents());
        dto.setOriginPriceCents(p.getOriginPriceCents());
        dto.setLimitPerUser(p.getLimitPerUser());
        dto.setStartTime(p.getStartTime());
        dto.setEndTime(p.getEndTime());
        dto.setStatus(p.getStatus());
        return Result.ok(dto);
    }

    // -------------------- 下单闸口（热点路径尽量不触库） --------------------

    @Override
    public Result<Long> gatePurchase(Long productId, Long userId, Integer quantity) {
        if (productId == null || userId == null) return Result.fail("参数错误");
        final int qty = (quantity == null || quantity <= 0) ? 1 : quantity;

        // 1) 先基于商品快照做“是否允许购买”的业务判断。
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

        // 2) 再执行 Lua，原子完成库存校验、限购校验和库存预扣。
        String stockKey = RedisConstants.STOCK_PREFIX + "{" + productId + "}";
        String buyKey = RedisConstants.USER_BUY_HASH + "{" + productId + "}";
        Long ret = stringRedisTemplate.execute(
                FLASH_GATE_SCRIPT,
                Arrays.asList(stockKey, buyKey),
                String.valueOf(userId),        // ARGV[1]
                String.valueOf(qty),           // ARGV[2]
                String.valueOf(limit)          // ARGV[3]
        );
        int code = (ret == null) ? -1 : ret.intValue();

        // 成功时返回秒杀价，让订单服务按同一份快照计算订单金额。
        if (code == 0) return Result.ok(p.getFlashPriceCents());

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
        // 事务提交后刷新缓存，避免出现旧值回填窗口。
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
        // 先读取商品，确认它存在并拿到当前库存。
        Result<ProductDTO> productDTOResult = getProduct(id);
        if(!productDTOResult.isSuccess() || productDTOResult.getData() == null){
            return Result.fail("商品不存在");
        }
        Integer stock = productDTOResult.getData().getInitStock();
        if(stock == null) return Result.fail("上架失败, 商品库存为 null");

        ProductDTO p = new ProductDTO();
        p.setId(id);
        p.setStatus(2);

        // 先更新数据库状态为 ONLINE，再在提交后处理 Redis 预热。
        Result<Void> update = update(p);
        if(!update.isSuccess()) return Result.fail("上架失败");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // setIfAbsent 避免误覆盖已有库存快照。
                    stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.STOCK_PREFIX + "{" + id + "}", String.valueOf(stock));
                    cacheClient.refreshAfterCommit(
                            CACHE_PRODUCT_KEY + id,
                            () -> productMapper.findById(id),
                            30, TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.warn("[onSale] 缓存库存失败", e);
                }
            }
        });
        return Result.ok();
    }

    @Override
    public Result restoreStock(Long productId, Long userId, Integer quantity) {
        // 库存恢复同样走 Lua，保证“加库存”和“减累计购买数”同时成功或同时失败。
        String stockKey = RedisConstants.STOCK_PREFIX + "{" + productId + "}";
        String buyKey = RedisConstants.USER_BUY_HASH + "{" + productId + "}";
        Long ret = stringRedisTemplate.execute(RESTORE_STOCK_SCRIPT,
                Arrays.asList(stockKey, buyKey),
                String.valueOf(userId),
                String.valueOf(quantity)
                );
        if(ret != 0){
            log.warn("[restoreStock] 恢复库存失败, userId: {}, productId: {}, quantity: {}", userId, productId, quantity);
            return Result.fail("恢复库存失败");
        }
        return Result.ok();
    }
}
