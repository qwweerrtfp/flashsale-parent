package com.ye94z.product.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ye94z.common.core.dto.ProductDTO;
import com.ye94z.common.core.dto.Result;
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
                                     @RequestHeader(name = "X-User-Id") Long userId,
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
     * 回补库存（取消/超时关单时调用）
     */
    @PostMapping("/stock/restore")
    public Result<Void> restoreStock(@RequestParam("productId") Long productId,
                                     @RequestParam("quantity") Integer quantity,
                                     @RequestParam("userId") Long userId) {
        return productService.restoreStock(productId, quantity, userId);
    }

    /**
     * 上架秒杀商品（包含 redis 库存预热）
     */
    @PostMapping("/update")
    public Result update(@RequestBody ProductDTO productDTO) {
        return productService.update(productDTO);
    }
}