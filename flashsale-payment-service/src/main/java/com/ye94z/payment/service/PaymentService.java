package com.ye94z.payment.service;

import com.ye94z.common.core.dto.Result;

public interface PaymentService {
    /**
     * 余额支付
     * @return Result.ok(txnId) on success
     */
    Result<Long> payOrder(Long userId, Long orderId, Long amountCents);
}