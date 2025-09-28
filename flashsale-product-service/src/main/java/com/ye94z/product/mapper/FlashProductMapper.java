package com.ye94z.product.mapper;

import com.ye94z.common.core.dto.ProductDTO;
import com.ye94z.product.entity.FlashProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlashProductMapper {

    /** 按ID查商品（用于闸口前的在线/时间校验、对外价格等） */
    FlashProduct findById(@Param("id") Long id);

    /** 回补库存（取消/超时关单时调用） */
    int incrStockDecrSold(@Param("productId") Long productId, @Param("qty") Integer qty);

    int decrStockIncrSold(Long productId, Integer quantity);

    /** 上架秒杀商品 */
    int onSale(Long productId);

    int update(ProductDTO productDTO);
}