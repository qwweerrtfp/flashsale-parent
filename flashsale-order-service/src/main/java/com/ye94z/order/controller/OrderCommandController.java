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
 * 订单命令接口。
 * 控制器层只做参数接收与用户身份提取，真正的业务编排下沉到 Service。
 */
@RestController
@RequestMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@AllArgsConstructor
public class OrderCommandController {

    private final OrderService orderService;

    /** 创建订单，成功后会快速返回订单号。 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result create(@RequestHeader(name = "X-User-Id", required = false) Long userId,
                         @Valid @RequestBody CreateOrderRequest req) {
        return orderService.requestOrder(userId, req);
    }

    /** 用户主动取消订单。 */
    @PostMapping("/{orderId}/cancel")
    public Result cancel(@RequestHeader("X-User-Id") Long userId,
                         @RequestBody CancelOrderMessage msg) {
        return orderService.requestCancel(userId, msg);
    }

    /** 发起支付命令，真正扣款由消息消费者异步调用支付服务完成。 */
    @PostMapping("/{orderId}/pay")
    public Result pay(@RequestHeader("X-User-Id") Long userId,
                      @PathVariable("orderId") @NotNull @Min(1) Long orderId) {
        return orderService.requestPay(userId, orderId);
    }
}
