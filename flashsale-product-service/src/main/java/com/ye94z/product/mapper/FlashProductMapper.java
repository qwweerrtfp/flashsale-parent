package com.ye94z.product.mapper;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.product.entity.FlashProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlashProductMapper {

    /** 按ID查商品（用于闸口前的在线/时间校验、对外价格等） */
    FlashProduct findById(@Param("id") Long id);

    int update(ProductDTO productDTO);
}