package com.ye94z.common.core.enums;

/** 支付方式：1余额；2支付宝；3微信（你的支付服务先实现余额） */
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

    public static PayType fromCode(Byte code) {
        if (code == null) return null;
        for (PayType p : values()) {
            if (p.code == code) return p;
        }
        return null;
    }
}