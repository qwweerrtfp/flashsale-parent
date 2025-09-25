package com.ye94z.order.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 支付成功事件载荷 */
public class PaymentPaidEvent implements Serializable {
    private Long orderId;
    private Long userId;
    private Long amountCents;
    private Long txnId;
    private LocalDateTime paidAt;

    public Long getOrderId() { return orderId; }
    public PaymentPaidEvent setOrderId(Long orderId) { this.orderId = orderId; return this; }

    public Long getUserId() { return userId; }
    public PaymentPaidEvent setUserId(Long userId) { this.userId = userId; return this; }

    public Long getAmountCents() { return amountCents; }
    public PaymentPaidEvent setAmountCents(Long amountCents) { this.amountCents = amountCents; return this; }

    public Long getTxnId() { return txnId; }
    public PaymentPaidEvent setTxnId(Long txnId) { this.txnId = txnId; return this; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public PaymentPaidEvent setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; return this; }
}