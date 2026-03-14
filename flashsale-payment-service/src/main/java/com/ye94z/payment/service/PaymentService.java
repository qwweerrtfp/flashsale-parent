package com.ye94z.payment.service;

import com.ye94z.common.core.pojo.Result;

public interface PaymentService {
    /**
     * 余额支付。
     * 成功时返回支付流水 ID，失败时返回业务错误原因。
     */
    Result<Long> payOrder(Long userId, Long orderId, Long amountCents);
}
