package com.ye94z.common.core.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 支付成功事件载荷 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPaidEventDTO implements Serializable {
    public Long orderId;
    public Long userId;
    public Long amountCents;
    public Long txnId;
    public LocalDateTime paidAt;
}