package com.ye94z.product.controller;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
import com.ye94z.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 对外（含 Feign）暴露的商品接口：
 * - POST /api/products/gate-purchase?productId=&userId=&quantity=
 * - GET  /api/products/{id}
 * - POST /api/products/stock/restore?productId=&quantity=
 */
@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    /**
     * 闸口校验（Lua 原子：库存、每人限购累计、原子扣减）
     */
    @PostMapping("/gate-purchase")
    public Result<Void> gatePurchase(@RequestParam("productId") Long productId,
                                     @RequestParam("userId") Long userId,
                                     @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity) {
        log.info("[gatePurchase] productId: {}, userId: {}, quantity: {}", productId, userId, quantity);
        return productService.gatePurchase(productId, userId, quantity);
    }

    /**
     * 商品详情（用于确认价格等关键字段）
     */
    @GetMapping("/{id}")
    public Result<ProductDTO> getProduct(@PathVariable("id") Long id) {
        return productService.getProduct(id);
    }

    /**
     * 上架秒杀商品（包含 redis 库存预热）
     */
    @PostMapping("/{id}/on-sale")
    public Result<Void> onSale(@PathVariable("id") Long id){
        return productService.onSale(id);
    }

    @PostMapping("/update")
    public Result update(@RequestBody ProductDTO productDTO) {
        return productService.update(productDTO);
    }

    @PostMapping("/stock/restoreStock")
    public Result restoreStock(@RequestParam("productId") Long productId,
                               @RequestParam("userId") Long userId,
                               @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity) {
        log.info("[restoreStock] productId: {}, quantity: {}", productId, quantity);
        return productService.restoreStock(productId, userId, quantity);
    }
}