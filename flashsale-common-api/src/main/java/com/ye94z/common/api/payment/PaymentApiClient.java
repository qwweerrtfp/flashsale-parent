package com.ye94z.common.api.payment;

import com.ye94z.common.core.pojo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 支付服务 Feign 契约。
 * 当前重点是余额支付，退款接口属于预留能力。
 */
@FeignClient(name = "flashsale-payment-service", contextId = "paymentApiClient", path = "/internal/payments")
public interface PaymentApiClient {

    /** 下单支付：扣减用户余额并生成支付流水。 */
    @PostMapping("/pay")
    Result<Long> pay(@RequestParam("userId") Long userId,
                     @RequestParam("orderId") Long orderId,
                     @RequestParam("amountCents") Long amountCents);

    /** 退款能力预留：把金额退回余额，并记录退款流水。 */
    @PostMapping("/refund")
    Result<Long> refund(@RequestParam("userId") Long userId,
                        @RequestParam("orderId") Long orderId,
                        @RequestParam("amountCents") Long amountCents,
                        @RequestParam(value = "idemKey", required = false) String idemKey);
}
