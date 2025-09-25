package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

/** 延时关单消息，携带 productId/quantity，避免取消时再查一次库 */
@Data
@Accessors(chain = true)
public class TimeoutOrderMessage {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Long userId;
}