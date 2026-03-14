package com.ye94z.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单实体，对应 flash_order 表。
 * 它是订单服务内部最核心的持久化对象，也是消息消费者最常读取的领域对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashOrder {

    /** 订单 ID（由雪花算法生成）。 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long productId;

    /** 数量(秒杀通常为1) */
    private Integer quantity;

    /** 应付金额(分) */
    private Long payAmountCents;

    /** 订单状态：1=UNPAID,2=PAID,3=FULFILLED,4=CANCELED,5=REFUNDING,6=REFUNDED */
    private Integer status;

    /** 支付渠道：1=BALANCE（本项目仅余额支付） */
    private Integer payChannel;

    /** 支付流水ID(支付库 txn.id) */
    private Long payTxnId;

    /** 业务幂等键(可选) */
    private String idempotencyKey;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 履约/发货/核销时间 */
    private LocalDateTime fulfillTime;

    /** 关单时间 */
    private LocalDateTime closeTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
