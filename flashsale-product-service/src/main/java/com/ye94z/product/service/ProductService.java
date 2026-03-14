package com.ye94z.product.service;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;

public interface ProductService {

    /** 秒杀闸口校验，成功时返回当前秒杀价。 */
    Result<Long> gatePurchase(Long productId, Long userId, Integer quantity);

    /** 获取商品详情。 */
    Result<ProductDTO> getProduct(Long id);

    /** 更新商品并刷新缓存。 */
    Result<Void> update(ProductDTO productDTO);

    /** 上架商品并初始化 Redis 库存。 */
    Result<Void> onSale(Long id);

    /** 回补库存与用户累计购买数。 */
    Result restoreStock(Long productId, Long userId, Integer quantity);
}
