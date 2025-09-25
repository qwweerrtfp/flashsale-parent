package com.ye94z.payment.entity;

import java.time.LocalDateTime;

public class WalletAccount {
    private Long id;
    private Long userId;
    private Long balanceCents;
    private Long freezeCents;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public WalletAccount setId(Long id) { this.id = id; return this; }

    public Long getUserId() { return userId; }
    public WalletAccount setUserId(Long userId) { this.userId = userId; return this; }

    public Long getBalanceCents() { return balanceCents; }
    public WalletAccount setBalanceCents(Long balanceCents) { this.balanceCents = balanceCents; return this; }

    public Long getFreezeCents() { return freezeCents; }
    public WalletAccount setFreezeCents(Long freezeCents) { this.freezeCents = freezeCents; return this; }

    public Integer getVersion() { return version; }
    public WalletAccount setVersion(Integer version) { this.version = version; return this; }

    public LocalDateTime getCreateTime() { return createTime; }
    public WalletAccount setCreateTime(LocalDateTime createTime) { this.createTime = createTime; return this; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public WalletAccount setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; return this; }
}