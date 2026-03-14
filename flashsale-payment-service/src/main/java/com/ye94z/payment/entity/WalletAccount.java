package com.ye94z.payment.entity;

import java.time.LocalDateTime;

public class WalletAccount {
    /** 主键 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 可用余额，单位分。 */
    private Long balanceCents;
    /** 冻结余额，预留给更复杂的支付场景。 */
    private Long freezeCents;
    /** 乐观锁版本号。 */
    private Integer version;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
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
