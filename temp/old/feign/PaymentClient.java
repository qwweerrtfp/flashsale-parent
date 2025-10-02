package com.ye94z.order.feign;

import com.ye94z.common.core.dto.Result;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 调用 payment-service 的钱包扣款/退款等接口。
 *
 * 假设 payment-service 暴露：
 *   POST /api/pay/wallet/debit    扣款（余额）
 *   POST /api/pay/wallet/credit   入账（退款）
 */
@FeignClient(
        name = "flashsale-payment-service",
        contextId = "paymentClient",
        path = "/api/pay/wallet",
        configuration = FeignCommonConfig.class
)
public interface PaymentClient {

    @PostMapping("/debit")
    Result<PayResponse> debit(@RequestBody PayRequest req);

    @PostMapping("/credit")
    Result<PayResponse> credit(@RequestBody PayRequest req);

    /* ---------- DTO ---------- */

    @Data
    class PayRequest {
        /** 必填：用户ID */
        private Long userId;
        /** 必填：订单ID（跨库逻辑关联） */
        private Long orderId;
        /** 必填：金额(分) */
        private Long amountCents;
        /** 必填：业务幂等键（建议 orderId + 动作标识） */
        private String idempotencyKey;
        /** 可选：渠道，默认 BALANCE=1 */
        private Integer channel = 1;
        /** 可选：备注 */
        private String remark;
    }

    @Data
    class PayResponse {
        /** 流水ID（pay_wallet_txn.id 或内部生成的 txnId） */
        private Long txnId;
        /** 交易结果：1=INIT,2=SUCCESS,3=FAILED（也可仅返回成功布尔） */
        private Integer status;
        /** 可选：失败原因 */
        private String message;
    }
}