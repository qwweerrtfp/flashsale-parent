package com.ye94z.common.api.product;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 面向 product-service 的 Feign API
 * 约定 product-service 暴露：
 * - POST /api/products/gate-purchase?productId=&userId=&quantity=
 * - GET  /api/products/{id}
 * - POST /api/products/stock/restore?productId=&quantity=
 */
@FeignClient(
        name = "flashsale-product-service",
        contextId = "productApiClient",
        path = "/products"
)
public interface ProductApiClient {

    /**
     * 秒杀闸口（Lua 原子校验/预扣/去重 等），true 表示允许继续下单
     */
    @PostMapping("/gate-purchase")
    Result<Boolean> gatePurchase(@RequestParam("productId") Long productId,
                                 @RequestParam("userId") Long userId,
                                 @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity);

    /**
     * 商品详情（用于确认秒杀价等关键信息）
     */
    @GetMapping("/{id}")
    Result<ProductDTO> getProduct(@PathVariable("id") Long productId);

    @PostMapping("/stock/restoreStock")
    Result restoreStock(@RequestParam("productId") Long productId,
                        @RequestParam("userId") Long userId,
                        @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity);
}