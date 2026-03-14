package com.ye94z.common.core.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付成功事件的消息体。
 * 该对象由 payment-service 发布，order-service 消费，
 * 字段尽量保持“订单侧完成状态回写所需的最小集合”。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPaidEventDTO implements Serializable {
    /** 支付对应的订单 ID。 */
    public Long orderId;
    /** 支付用户 ID，用于订单侧做归属校验或审计。 */
    public Long userId;
    /** 本次实际支付金额，单位分。 */
    public Long amountCents;
    /** 支付流水 ID，订单侧会回写到订单表。 */
    public Long txnId;
    /** 支付完成时间。 */
    public LocalDateTime paidAt;
}
