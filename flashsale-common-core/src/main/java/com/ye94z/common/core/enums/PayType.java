package com.ye94z.common.core.enums;

/**
 * 支付方式枚举。
 * 当前项目真正落地的是余额支付，其他渠道更多是为后续扩展预留编码位。
 */
public enum PayType {
    BALANCE((byte)1, "余额"),
    ALIPAY((byte)2, "支付宝"),
    WECHAT((byte)3, "微信");

    private final byte code;
    private final String desc;

    PayType(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public byte getCode() { return code; }
    public String getDesc() { return desc; }

    /** 将数据库或消息中的支付方式编码转换成枚举。 */
    public static PayType fromCode(Byte code) {
        if (code == null) return null;
        for (PayType p : values()) {
            if (p.code == code) return p;
        }
        return null;
    }
}
