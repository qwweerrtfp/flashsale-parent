package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlaceOrderMessage {
    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer quantity;
}