package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlaceOrderMessage {
    /** 订单 ID。 */
    private Long orderId;
    /** 用户 ID。 */
    private Long userId;
    /** 商品 ID。 */
    private Long productId;
    /** 购买数量。 */
    private Integer quantity;
}
