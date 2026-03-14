package com.ye94z.common.core.enums;

/**
 * 订单状态枚举。
 * 数据库存的是数字状态码，代码里尽量通过枚举表达语义，
 * 这样在消息消费、状态流转和页面展示时更容易看懂。
 */
public enum OrderStatus {
    UNPAID((byte)1, "未支付"),
    PAID((byte)2, "已支付"),
    USED((byte)3, "已核销"),
    CANCELED((byte)4, "已取消"),
    REFUNDING((byte)5, "退款中"),
    REFUNDED((byte)6, "已退款");

    private final byte code;
    private final String desc;

    OrderStatus(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public byte getCode() { return code; }
    public String getDesc() { return desc; }

    /** 将数据库里的状态码转换成枚举，便于业务层阅读。 */
    public static OrderStatus fromCode(Byte code) {
        if (code == null) return null;
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
