package com.ye94z.order.service.impl;

import com.ye94z.common.api.product.ProductApiClient;
import com.ye94z.common.core.dto.Result;
import com.ye94z.common.core.utils.SnowflakeIdGenerator;
import com.ye94z.order.entity.CreateOrderRequest;
import com.ye94z.order.mapper.FlashOrderMapper;
import com.ye94z.order.mq.OrderCommandProducer;
import com.ye94z.order.mq.msg.CancelOrderMessage;
import com.ye94z.order.mq.msg.PayOrderMessage;
import com.ye94z.order.mq.msg.PlaceOrderMessage;
import com.ye94z.order.mq.msg.TimeoutOrderMessage;
import com.ye94z.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ProductApiClient productApiClient;
    private final OrderCommandProducer producer;

    // 简单雪花：可后续抽到单例或注入
    private final SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1, 1);

    /** 超时关单时长（毫秒） */
    private static final long ORDER_TIMEOUT_MS = 15 * 60 * 1000L;
    private final FlashOrderMapper flashOrderMapper;

    @Override
    public Result placeOrderAsync(Long userId, CreateOrderRequest req) {
        if (userId == null || req == null || req.getProductId() == null) {
            return Result.fail("参数错误");
        }
        int qty = (req.getQuantity() == null || req.getQuantity() <= 0) ? 1 : req.getQuantity();

        // 1) 调用 product-service 的“闸口”校验（Lua 内部：库存、用户累计购买数<=限购、扣库存&累计）
        Result<Boolean> gate = productApiClient.gatePurchase(req.getProductId(), userId, qty);
        if (gate == null || !gate.isSuccess() || Boolean.FALSE.equals(gate.getData())) {
            String msg = (gate != null && gate.getErrorMsg() != null) ? gate.getErrorMsg() : "不满足购买条件";
            return Result.fail(msg);
        }

        // 2) 生成订单号（异步落库）
        long orderId = idGen.nextId();

        // 3) 发“创建订单”命令（异步落库&金额以 product 价格为准）
        PlaceOrderMessage create = new PlaceOrderMessage()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId(req.getProductId())
                .setQuantity(qty);
        producer.sendCreate(create);

        // 4) 发“超时关单”延时命令（夹带 productId/qty，取消时可不查库）
        TimeoutOrderMessage to = new TimeoutOrderMessage()
                .setOrderId(orderId)
                .setProductId(req.getProductId())
                .setQuantity(qty)
                .setUserId(userId);
        producer.sendTimeout(to, ORDER_TIMEOUT_MS);

        // 5) 立即返回
        return Result.ok(orderId);
    }

    @Override
    public Result requestCancel(Long userId, Long orderId) {
        if (userId == null || orderId == null) return Result.fail("参数错误");
        producer.sendCancel(new CancelOrderMessage().setOrderId(orderId).setUserId(userId));
        return Result.ok();
    }

    @Override
    public Result requestPay(Long userId, Long orderId) {
        if (userId == null || orderId == null) return Result.fail("参数错误");
        // 不在这里查 DB 的 payAmountCents，交给 onPay 消费者读取，避免下单未落库的竞态
        producer.sendPay(new PayOrderMessage()
                .setOrderId(orderId)
                .setUserId(userId));
        return Result.ok("已受理");
    }

    @Override
    public Result getDetail(Long userId, Long orderId) {
        return Result.fail("未实现：请在 Query 层/Mapper 实现订单详情查询并做归属校验");
    }

    @Override
    public Result listMyOrders(Long userId, Integer page, Integer size, Integer status) {
        return Result.fail("未实现：请在 Query 层/Mapper 实现分页列表");
    }
}