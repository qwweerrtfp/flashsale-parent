package com.ye94z.payment.controller;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付服务内部命令接口。
 * 当前主要由 order-service 调用，不直接作为前端公开 API。
 */
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentCommandController {

    private final PaymentService paymentService;

    /** 余额支付入口，成功时返回支付流水 ID。 */
    @PostMapping("/pay")
    public Result<Long> pay(@RequestParam("userId") Long userId,
                            @RequestParam("orderId") Long orderId,
                            @RequestParam("amountCents") Long amountCents) {
        return paymentService.payOrder(userId, orderId, amountCents);
    }
}
