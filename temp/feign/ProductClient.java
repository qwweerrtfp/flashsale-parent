package com.ye94z.order.feign;

import com.ye94z.common.core.dto.Result;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 调用 product-service 查询秒杀商品详情（用于下单前的展示/快照）。
 *
 * 假设 product-service 暴露的接口为：
 *   GET /api/products/{id}
 * 返回 Result<ProductView>
 */
@FeignClient(
        name = "flashsale-product-service",
        contextId = "productClient",
        path = "/api/products",
        configuration = FeignCommonConfig.class
)
public interface ProductClient {

    @GetMapping("/{id}")
    Result<ProductView> getById(@PathVariable("id") Long id);

    /**
     * 可在这里扩展其他需要的下游接口，如扣减库存（若不走 Redis/Lua）等。
     * 但当前你的下单链路已经在 Redis 中做了闸口，不再建议直接 Feign 扣减库存。
     */

    /**
     * 下游返回的商品视图，仅包含下单需要用到的关键字段
     * （如需更多字段，按 flash_product 表扩展）
     */
    @Data
    class ProductView {
        private Long id;
        private String title;
        private Long flashPriceCents;
        private Long originPriceCents;
        private Integer stock;
        private Integer sold;
        private Integer limitPerUser;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer status;   // 1=DRAFT,2=ONLINE,3=OFFLINE
        private Integer version;  // 可用于客户端感知版本
        private String images;    // 可选：封面/图集
        private String subtitle;  // 可选
        private String description; // 可选
    }
}