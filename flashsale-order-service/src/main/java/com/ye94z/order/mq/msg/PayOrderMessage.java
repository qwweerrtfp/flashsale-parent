package com.ye94z.order.mq.msg;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PayOrderMessage {
    /** 订单 ID。 */
    private Long orderId;
    /** 用户 ID。 */
    private Long userId;
}
