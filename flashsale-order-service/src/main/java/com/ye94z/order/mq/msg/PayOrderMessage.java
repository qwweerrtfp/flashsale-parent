package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PayOrderMessage {
    private Long orderId;
    private Long userId;
    private Long payAmountCents;
}