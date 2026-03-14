package com.ye94z.product.controller;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
import com.ye94z.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 商品服务对外接口。
 * 既供前端访问，也供 order-service 通过 Feign 做内部调用。
 */
@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    /** 秒杀闸口：做活动校验、限购校验并原子预扣 Redis 库存。 */
    @PostMapping("/gate-purchase")
    public Result<Long> gatePurchase(@RequestParam("productId") Long productId,
                                     @RequestParam("userId") Long userId,
                                     @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity) {
        log.info("[gatePurchase] productId: {}, userId: {}, quantity: {}", productId, userId, quantity);
        return productService.gatePurchase(productId, userId, quantity);
    }

    /** 查询商品详情，供页面展示和订单服务确认价格使用。 */
    @GetMapping("/{id}")
    public Result<ProductDTO> getProduct(@PathVariable("id") Long id) {
        return productService.getProduct(id);
    }

    /** 商品上架，并把库存预热到 Redis。 */
    @PostMapping("/{id}/on-sale")
    public Result<Void> onSale(@PathVariable("id") Long id){
        return productService.onSale(id);
    }

    /** 更新商品，更新成功后会刷新商品缓存。 */
    @PostMapping("/update")
    public Result update(@RequestBody ProductDTO productDTO) {
        return productService.update(productDTO);
    }

    /** 回补库存，通常由取消订单或超时关单触发。 */
    @PostMapping("/stock/restoreStock")
    public Result restoreStock(@RequestParam("productId") Long productId,
                               @RequestParam("userId") Long userId,
                               @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity) {
        log.info("[restoreStock] productId: {}, quantity: {}", productId, quantity);
        return productService.restoreStock(productId, userId, quantity);
    }
}
