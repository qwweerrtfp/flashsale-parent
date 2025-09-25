package com.ye94z.payment.controller;

import com.ye94z.common.core.dto.Result;
import com.ye94z.payment.service.PaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 内部调用接口：余额支付
 * POST /internal/payments/pay
 */
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentCommandController {

    private final PaymentService paymentService;

    @PostMapping("/pay")
    public Result<Long> pay(@Validated @RequestBody PayRequest req) {
        return paymentService.payOrder(req.getUserId(), req.getOrderId(), req.getAmountCents());
    }

    @Data
    public static class PayRequest {
        @NotNull private Long userId;
        @NotNull private Long orderId;
        @NotNull @Min(1) private Long amountCents;
    }
}