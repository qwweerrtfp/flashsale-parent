package com.ye94z.order.service;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.order.entity.CreateOrderRequest;
import com.ye94z.order.mq.msg.CancelOrderMessage;

/**
 * 订单领域服务。
 * 命令侧负责串联商品、支付和 MQ；查询侧统一收口，便于后续扩展。
 */
public interface OrderService {

    /** 下单，成功时返回新生成的 orderId。 */
    Result requestOrder(Long userId, CreateOrderRequest req);

    /** 用户主动取消，仅未支付订单允许成功。 */
    Result requestCancel(Long userId, CancelOrderMessage msg);

    /** 发起支付命令。 */
    Result requestPay(Long userId, Long orderId);

    /** 查询订单详情。 */
    Result getDetail(Long userId, Long orderId);

    /** 查询我的订单列表。 */
    Result listMyOrders(Long userId, Integer page, Integer size, Integer status);
}
