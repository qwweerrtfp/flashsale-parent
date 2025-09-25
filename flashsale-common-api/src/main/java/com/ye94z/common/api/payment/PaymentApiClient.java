package com.ye94z.common.api.payment;

import com.ye94z.common.core.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 钱包支付契约（仅余额通道）：
 * - 为避免在 API 模块新增 DTO，这里先用请求参数的形式；若你在 core 定义了 PayRequestDTO/PayResultDTO，可改成 @RequestBody。
 */
@FeignClient(name = "flashsale-payment-service", contextId = "paymentApiClient", path = "/api/payments/wallet")
public interface PaymentApiClient {

    /**
     * 下单支付：扣减用户余额并生成支付流水。
     * @param userId        用户ID
     * @param orderId       订单ID（跨库逻辑关联）
     * @param amountCents   金额(分)
     * @return               成功返回支付流水ID(txnId)
     */
    @PostMapping("/pay")
    Result<Long> pay(@RequestParam("userId") Long userId,
                     @RequestParam("orderId") Long orderId,
                     @RequestParam("amountCents") Long amountCents);

    /**
     * 退款：将金额退回余额，并记录退款流水。
     */
    @PostMapping("/refund")
    Result<Long> refund(@RequestParam("userId") Long userId,
                        @RequestParam("orderId") Long orderId,
                        @RequestParam("amountCents") Long amountCents,
                        @RequestParam(value = "idemKey", required = false) String idemKey);
}