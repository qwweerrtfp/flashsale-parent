package com.ye94z.order.controller;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.order.entity.CreateOrderRequest;
import com.ye94z.order.mq.msg.CancelOrderMessage;
import com.ye94z.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 命令接口：创建/取消/支付
 * - 仅做参数接收 & 调用 service；业务在 OrderServiceImpl
 */
@RestController
@RequestMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@AllArgsConstructor
public class OrderCommandController {

    private final OrderService orderService;

    /**
     * 下单（异步）：快速返回订单号
     * userId 建议从网关透传（如 X-User-Id），这里做兜底
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result create(@RequestHeader(name = "X-User-Id", required = false) Long userId,
                         @Valid @RequestBody CreateOrderRequest req) {
        return orderService.requestOrder(userId, req);
    }

    /**
     * 主动取消（手动取消，非延时关单）
     */
    @PostMapping("/{orderId}/cancel")
    public Result cancel(@RequestHeader("X-User-Id") Long userId,
                         @RequestBody CancelOrderMessage msg) {
        return orderService.requestCancel(userId, msg);
    }

    /**
     * 模拟发起支付（余额支付由 payment-service 处理；本接口触发统一流程）
     * 实际支付成功会回调 order-service（另有回调接口或直接由 payment-service 调用 Feign）
     */
    @PostMapping("/{orderId}/pay")
    public Result pay(@RequestHeader("X-User-Id") Long userId,
                      @PathVariable("orderId") @NotNull @Min(1) Long orderId) {
        return orderService.requestPay(userId, orderId);
    }
}