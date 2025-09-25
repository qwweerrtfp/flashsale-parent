package com.ye94z.order.entity;

import lombok.Data;

/**
 * 下单请求体（放在 entity 包按你的要求归档）
 */
@Data
public class CreateOrderRequest {
    /** 商品ID（必填） */
    private Long productId;
    /** 购买数量（默认由控制层兜底为 1） */
    private Integer quantity;
    /** 业务幂等键（可选） */
    private String idempotencyKey;
}