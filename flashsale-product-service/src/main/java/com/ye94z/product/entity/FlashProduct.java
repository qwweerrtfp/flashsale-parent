package com.ye94z.product.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品实体，对应 flash_product 表。
 * 它是 product-service 内部的持久化对象，不直接暴露给外部接口。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlashProduct {
    private Long id;                    // 商品ID
    private String title;               // 标题
    private String subtitle;            // 副标题
    private String images;              // 图片（逗号分隔或JSON）
    private String description;         // 描述
    private Long flashPriceCents;       // 秒杀价(分)
    private Long originPriceCents;      // 原价(分)
    private Integer stock;              // 库存
    private Integer sold;               // 已售
    private Integer limitPerUser;       // 每人限购
    private LocalDateTime startTime;    // 开始时间
    private LocalDateTime endTime;      // 结束时间
    private Integer status;             // 1=DRAFT,2=ONLINE,3=OFFLINE
    private Integer version;            // 乐观锁版本(可选)
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}
