package com.ye94z.payment.entity;

import java.time.LocalDateTime;

public class WalletTxn {
    private Long id;
    private Long userId;
    private Long orderId;
    private Long amountCents;
    private Integer direction; // 1=DEBIT,2=CREDIT
    private Integer status;    // 1=INIT,2=SUCCESS,3=FAILED
    private Integer bizType;   // 1=PAY_ORDER
    private Integer channel;   // 1=BALANCE
    private String idempotencyKey;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public WalletTxn setId(Long id) { this.id = id; return this; }

    public Long getUserId() { return userId; }
    public WalletTxn setUserId(Long userId) { this.userId = userId; return this; }

    public Long getOrderId() { return orderId; }
    public WalletTxn setOrderId(Long orderId) { this.orderId = orderId; return this; }

    public Long getAmountCents() { return amountCents; }
    public WalletTxn setAmountCents(Long amountCents) { this.amountCents = amountCents; return this; }

    public Integer getDirection() { return direction; }
    public WalletTxn setDirection(Integer direction) { this.direction = direction; return this; }

    public Integer getStatus() { return status; }
    public WalletTxn setStatus(Integer status) { this.status = status; return this; }

    public Integer getBizType() { return bizType; }
    public WalletTxn setBizType(Integer bizType) { this.bizType = bizType; return this; }

    public Integer getChannel() { return channel; }
    public WalletTxn setChannel(Integer channel) { this.channel = channel; return this; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public WalletTxn setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

    public String getRemark() { return remark; }
    public WalletTxn setRemark(String remark) { this.remark = remark; return this; }

    public LocalDateTime getCreateTime() { return createTime; }
    public WalletTxn setCreateTime(LocalDateTime createTime) { this.createTime = createTime; return this; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public WalletTxn setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; return this; }
}