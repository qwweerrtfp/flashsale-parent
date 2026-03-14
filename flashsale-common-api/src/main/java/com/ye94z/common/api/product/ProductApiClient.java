package com.ye94z.common.api.product;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 面向 product-service 的 Feign 契约。
 * order-service 通过它调用商品服务的秒杀闸口、商品详情和库存回补接口。
 */
@FeignClient(
        name = "flashsale-product-service",
        contextId = "productApiClient",
        path = "/products"
)
public interface ProductApiClient {

    /** 秒杀闸口校验，成功时返回当前秒杀价。 */
    @PostMapping("/gate-purchase")
    Result<Long> gatePurchase(@RequestParam("productId") Long productId,
                                 @RequestParam("userId") Long userId,
                                 @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity);

    /** 查询商品详情，主要给订单侧确认价格与活动配置。 */
    @GetMapping("/{id}")
    Result<ProductDTO> getProduct(@PathVariable("id") Long productId);

    /** 恢复库存与用户累计购买数。 */
    @PostMapping("/stock/restoreStock")
    Result restoreStock(@RequestParam("productId") Long productId,
                        @RequestParam("userId") Long userId,
                        @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity);
}
