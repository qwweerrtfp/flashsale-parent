package com.ye94z.product.mapper;

import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.product.entity.FlashProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlashProductMapper {

    /** 按 ID 查询商品，用于详情接口和下单前校验。 */
    FlashProduct findById(@Param("id") Long id);

    /** 更新商品，具体动态 SQL 在 XML 中维护。 */
    int update(ProductDTO productDTO);
}
