package com.ye94z.order.controller;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.order.service.OrderService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 订单查询接口。
 * 当前入口已经预留，但 Service 层查询实现尚未完成。
 */
@RestController
@RequestMapping(value = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class OrderQueryController {

    private final OrderService orderService;

    public OrderQueryController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 查询订单详情。 */
    @GetMapping("/{orderId}")
    public Result detail(@RequestHeader("X-User-Id") Long userId,
                         @PathVariable("orderId") @NotNull @Min(1) Long orderId) {
        return orderService.getDetail(userId, orderId);
    }

    /**
     * 我的订单列表（简单分页）
     * 可选 status：1=UNPAID,2=PAID,3=FULFILLED,4=CANCELED,5=REFUNDING,6=REFUNDED
     */
    @GetMapping("/my")
    public Result myOrders(@RequestHeader("X-User-Id") Long userId,
                           @RequestParam(defaultValue = "1") @Min(1) Integer page,
                           @RequestParam(defaultValue = "10") @Min(1) Integer size,
                           @RequestParam(required = false) Integer status) {
        return orderService.listMyOrders(userId, page, size, status);
    }
}
