package com.ye94z.order.service;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.order.entity.CreateOrderRequest;
import com.ye94z.order.mq.msg.CancelOrderMessage;

/**
 * 订单领域服务（命令+查询）
 * - placeOrderAsync：异步下单，快速返回 orderId
 * - requestCancel  ：用户主动取消（仅 UNPAID -> CANCELED）
 * - requestPay     ：发起支付（由 payment-service 处理，成功后异步回调）
 * - getDetail      ：订单详情
 * - listMyOrders  ：我的订单分页
 */
public interface OrderService {

    /** 下单 */
    Result requestOrder(Long userId, CreateOrderRequest req);

    /** 用户主动取消（仅未支付可取消） */
    Result requestCancel(Long userId, CancelOrderMessage msg);

    /** 发起支付（余额渠道），实际扣款由 payment-service 完成 */
    Result requestPay(Long userId, Long orderId);

    /** 订单详情（含基本鉴权） */
    Result getDetail(Long userId, Long orderId);

    /** 我的订单分页（可选 status 过滤） */
    Result listMyOrders(Long userId, Integer page, Integer size, Integer status);
}