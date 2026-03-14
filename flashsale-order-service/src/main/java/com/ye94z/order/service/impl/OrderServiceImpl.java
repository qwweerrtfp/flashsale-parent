package com.ye94z.order.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye94z.common.api.product.ProductApiClient;
import com.ye94z.common.core.pojo.ProductDTO;
import com.ye94z.common.core.pojo.Result;
import com.ye94z.common.core.utils.SnowflakeIdGenerator;
import com.ye94z.order.entity.CreateOrderRequest;
import com.ye94z.order.entity.FlashOrder;
import com.ye94z.order.mapper.FlashOrderMapper;
import com.ye94z.order.mq.OrderCommandProducer;
import com.ye94z.order.mq.msg.CancelOrderMessage;
import com.ye94z.order.mq.msg.PayOrderMessage;
import com.ye94z.order.mq.msg.TimeoutOrderMessage;
import com.ye94z.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 商品服务 Feign：负责闸口校验与库存回补。 */
    private final ProductApiClient productApiClient;
    /** 订单消息生产者。 */
    private final OrderCommandProducer producer;

    // 当前直接内置雪花 ID 生成器，满足示例项目的分布式订单号需求。
    private final SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1, 1);

    /** 超时关单时长。 */
    private static final long ORDER_TIMEOUT_MS = 15 * 60 * 1000L;
    /** 订单表访问层。 */
    private final FlashOrderMapper orderMapper;

    /** 订单初始状态：未支付。 */
    private static final int STATUS_UNPAID = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result requestOrder(Long userId, CreateOrderRequest req) {
        if (userId == null || req == null || req.getProductId() == null) {
            return Result.fail("参数错误");
        }
        int qty = (req.getQuantity() == null || req.getQuantity() <= 0) ? 1 : req.getQuantity();

        // 1) 下单前先让商品服务做资格校验和库存预扣，避免订单服务直接参与热点库存竞争。
        Result<Long> gate = productApiClient.gatePurchase(req.getProductId(), userId, qty);
        if (gate == null || !gate.isSuccess()) {
            String msg = (gate != null && gate.getErrorMsg() != null) ? gate.getErrorMsg() : "不满足购买条件";
            return Result.fail(msg);
        }

        // 2) 基于商品服务返回的秒杀价计算订单金额，并生成订单号。
        long orderId = idGen.nextId();
        Long flashPrice = gate.getData();
        flashPrice = flashPrice == null ? 0L : flashPrice;
        long amount = qty * flashPrice;

        FlashOrder order = new FlashOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setProductId(req.getProductId());
        order.setQuantity(qty);
        order.setPayAmountCents(amount);
        order.setStatus(STATUS_UNPAID);

        try {
            orderMapper.insert(order);
        } catch (Exception e) {
            // 订单落库失败时，要把已经预扣的库存归还回去。
            productApiClient.restoreStock(req.getProductId(), userId, qty);
            throw e;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 3) 事务提交后再发送延时关单消息，避免“消息先发、订单没落库”的不一致。
                TimeoutOrderMessage to = new TimeoutOrderMessage()
                        .setOrderId(orderId)
                        .setProductId(req.getProductId())
                        .setQuantity(qty)
                        .setUserId(userId);
                producer.sendTimeout(to, ORDER_TIMEOUT_MS);
            }
        });
        // 4) 到这里订单已经成功创建，接口可以立即返回订单号。
        return Result.ok(orderId);
    }

    @Override
    public Result requestCancel(Long userId, CancelOrderMessage msg) {
        if (userId == null || msg.getOrderId() == null) return Result.fail("参数错误");
        // 用户身份以后端透传的 userId 为准，不信任请求体中的用户字段。
        msg.setUserId(userId);
        producer.sendCancel(msg);
        return Result.ok();
    }

    @Override
    public Result requestPay(Long userId, Long orderId) {
        if (userId == null || orderId == null) return Result.fail("参数错误");
        // 不在这里同步查订单金额，把读库动作放到消费者里做，避免和下单事务时序打架。
        producer.sendPay(new PayOrderMessage()
                .setOrderId(orderId)
                .setUserId(userId));
        return Result.ok("已受理");
    }

    @Override
    public Result getDetail(Long userId, Long orderId) {
        // 查询逻辑预留在这里，后续可补订单归属校验和 DTO 装配。
        return Result.fail("未实现：请在 Query 层/Mapper 实现订单详情查询并做归属校验");
    }

    @Override
    public Result listMyOrders(Long userId, Integer page, Integer size, Integer status) {
        // 当前先明确告知未实现，避免调用方误判为功能已可用。
        return Result.fail("未实现：请在 Query 层/Mapper 实现分页列表");
    }
}
