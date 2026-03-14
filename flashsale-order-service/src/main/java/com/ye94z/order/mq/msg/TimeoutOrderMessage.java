package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

/** 延时关单消息，携带 productId/quantity，避免取消时再查一次库 */
@Data
@Accessors(chain = true)
public class TimeoutOrderMessage {
    /** 订单 ID。 */
    private Long orderId;
    /** 商品 ID。 */
    private Long productId;
    /** 预占库存数量。 */
    private Integer quantity;
    /** 用户 ID。 */
    private Long userId;
}
