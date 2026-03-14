package com.ye94z.order.entity;

import lombok.Data;

/**
 * 下单请求体。
 * 这里只保留真正需要由调用方提供的字段，金额等敏感信息统一由后端确认。
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
