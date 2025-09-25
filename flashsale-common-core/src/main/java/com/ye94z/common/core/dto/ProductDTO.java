package com.ye94z.common.core.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品对外传输对象（DTO）
 * - 字段与 flash_product 表对应（做了驼峰化）
 * - 不依赖任何持久化框架，便于放在 common-core 供各模块复用
 *
 * 建议：由各服务侧（product-service）在 Controller/Service 中完成
 * Entity <-> DTO 的转换，避免 common-core 反向依赖业务实体。
 */
public class ProductDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long id;

    /** 商品标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 图片（逗号分隔或JSON） */
    private String images;

    /** 描述 */
    private String description;

    /** 秒杀价(分) */
    private Long flashPriceCents;

    /** 原价(分) */
    private Long originPriceCents;

    /** 库存(总量) */
    private Integer stock;

    /** 已售 */
    private Integer sold;

    /** 每人限购 */
    private Integer limitPerUser;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：1=DRAFT,2=ONLINE,3=OFFLINE */
    private Integer status;

    /** 版本（乐观锁，可选） */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ---------- 便捷方法（可选） ----------

    /**
     * 是否处于可抢购窗口（仅做简单时间与状态判断，具体可在业务层再校验库存等）
     */
    public boolean isOnSaleNow() {
        if (status == null || startTime == null || endTime == null) return false;
        if (status != 2) return false; // ONLINE
        LocalDateTime now = LocalDateTime.now();
        return (now.isEqual(startTime) || now.isAfter(startTime))
                && (now.isBefore(endTime) || now.isEqual(endTime));
    }

    // ---------- Getter / Setter ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getFlashPriceCents() { return flashPriceCents; }
    public void setFlashPriceCents(Long flashPriceCents) { this.flashPriceCents = flashPriceCents; }

    public Long getOriginPriceCents() { return originPriceCents; }
    public void setOriginPriceCents(Long originPriceCents) { this.originPriceCents = originPriceCents; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Integer getSold() { return sold; }
    public void setSold(Integer sold) { this.sold = sold; }

    public Integer getLimitPerUser() { return limitPerUser; }
    public void setLimitPerUser(Integer limitPerUser) { this.limitPerUser = limitPerUser; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}