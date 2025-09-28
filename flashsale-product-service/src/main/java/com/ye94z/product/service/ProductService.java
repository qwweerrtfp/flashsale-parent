package com.ye94z.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ye94z.common.core.dto.ProductDTO;
import com.ye94z.common.core.dto.Result;
import org.springframework.transaction.annotation.Transactional;

public interface ProductService {

    /** 闸口校验（Lua 原子校验 + 预扣） */
    Result<Void> gatePurchase(Long productId, Long userId, Integer quantity);

    /** 获取商品详情（给下单侧确认价格/限购等） */
    Result<ProductDTO> getProduct(Long id);

    /** 扣减库存-DB */
    @Transactional(rollbackFor = Exception.class)
    Result<Void> deductStock(Long productId, Integer quantity);

    /** 回补库存（取消/超时关单） */
    Result<Void> restoreStock(Long productId, Integer quantity, Long userId);

    Result update(ProductDTO productDTO);
}