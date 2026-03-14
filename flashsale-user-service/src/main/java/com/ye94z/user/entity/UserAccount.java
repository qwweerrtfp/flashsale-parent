package com.ye94z.user.entity;

import java.time.LocalDateTime;

/**
 * 用户实体，映射 user_account 表。
 * 这里使用的是最朴素的 JavaBean 写法，便于 MyBatis 直接做属性映射。
 */
public class UserAccount {
    /** 主键 ID。 */
    private Long id;
    /** 用户手机号，也是当前登录体系里的唯一身份标识。 */
    private String phone;
    /** 密码哈希。当前验证码登录流程里未使用，属于扩展字段。 */
    private String passwordHash;
    /** 用户昵称。 */
    private String nickname;
    /** 头像地址。 */
    private String avatarUrl;
    /** 用户状态：1=ACTIVE,0=DISABLED。 */
    private Short status;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Short getStatus() { return status; }
    public void setStatus(Short status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
