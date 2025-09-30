package com.ye94z.common.core.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 支付成功事件载荷 */
public class PaymentPaidEventDTO implements Serializable {
    public Long orderId;
    public Long userId;
    public Long amountCents;
    public Long txnId;
    public LocalDateTime paidAt;


    public PaymentPaidEventDTO() {
    }

    public PaymentPaidEventDTO(Long orderId, Long userId, Long amountCents, Long txnId, LocalDateTime paidAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.amountCents = amountCents;
        this.txnId = txnId;
        this.paidAt = paidAt;
    }

    /**
     * 获取
     * @return orderId
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * 设置
     * @param orderId
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * 获取
     * @return userId
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置
     * @param userId
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取
     * @return amountCents
     */
    public Long getAmountCents() {
        return amountCents;
    }

    /**
     * 设置
     * @param amountCents
     */
    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    /**
     * 获取
     * @return txnId
     */
    public Long getTxnId() {
        return txnId;
    }

    /**
     * 设置
     * @param txnId
     */
    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    /**
     * 获取
     * @return paidAt
     */
    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    /**
     * 设置
     * @param paidAt
     */
    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String toString() {
        return "PaymentPaidEventDTO{orderId = " + orderId + ", userId = " + userId + ", amountCents = " + amountCents + ", txnId = " + txnId + ", paidAt = " + paidAt + "}";
    }
}