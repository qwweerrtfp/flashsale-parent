package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CancelOrderMessage {
    private Long orderId;
    private Long userId;
}