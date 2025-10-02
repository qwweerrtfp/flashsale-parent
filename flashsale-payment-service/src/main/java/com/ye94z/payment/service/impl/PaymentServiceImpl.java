package com.ye94z.payment.service.impl;

import com.ye94z.common.core.constants.RedisConstants;
import com.ye94z.common.core.pojo.PaymentPaidEventDTO;
import com.ye94z.common.core.pojo.Result;
import com.ye94z.payment.entity.WalletAccount;
import com.ye94z.payment.entity.WalletTxn;
import com.ye94z.payment.mapper.WalletAccountMapper;
import com.ye94z.payment.mapper.WalletTxnMapper;
import com.ye94z.payment.mq.PaymentEventProducer;
import com.ye94z.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final WalletAccountMapper accountMapper;
    private final WalletTxnMapper txnMapper;
    private final PaymentEventProducer producer;
    private final StringRedisTemplate redisTemplate;

    private static final int DIR_DEBIT = 1;
    private static final int ST_SUCCESS = 2;
    private static final int BIZ_PAY_ORDER = 1;
    private static final int CH_BALANCE = 1;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Long> payOrder(Long userId, Long orderId, Long amountCents) {
        if (userId == null || orderId == null || amountCents == null || amountCents <= 0) {
            return Result.fail("参数错误");
        }

        // 幂等：若已存在该订单的成功流水，直接返回
        WalletTxn exists = txnMapper.findByOrderId(orderId);
        if (exists != null && exists.getStatus() != null && exists.getStatus() == ST_SUCCESS) {
            return Result.fail("paid, txnId = " + exists.getId());
        }

        // 扣减余额（乐观锁重试）
        WalletAccount acc = accountMapper.findByUserId(userId);
        if (acc == null) return Result.fail("钱包不存在");
        if (acc.getBalanceCents() == null || acc.getBalanceCents() < amountCents) {
            return Result.fail("余额不足");
        }

        int retry = 3;
        boolean deducted = false;
        while (retry-- > 0) {
            int n = accountMapper.deductBalance(userId, amountCents, acc.getVersion());
            if (n > 0) { deducted = true; break; }
            // 版本冲突，重读+重试
            acc = accountMapper.findByUserId(userId);
            if (acc == null || acc.getBalanceCents() < amountCents) return Result.fail("余额不足");
        }
        if (!deducted) return Result.fail("支付繁忙，请重试");

        // 记成功流水
        WalletTxn txn = new WalletTxn()
                .setUserId(userId)
                .setOrderId(orderId)
                .setAmountCents(amountCents)
                .setDirection(DIR_DEBIT)
                .setStatus(ST_SUCCESS)
                .setBizType(BIZ_PAY_ORDER)
                .setChannel(CH_BALANCE)
                .setRemark("balance pay success");

        try {
            txnMapper.insert(txn); // 唯一键: order_id
        } catch (DuplicateKeyException dup) {
            // 幂等：已经有这笔订单的流水
            WalletTxn existed = txnMapper.findByOrderId(orderId);
            if (existed != null && existed.getStatus() == ST_SUCCESS) {
                // 不再发布事件（很可能已发布过）；直接返回成功与原txnId
                return Result.ok(existed.getId());
            }
            // 如果查到是 INIT/FAILED 等中间态，你可以返回“处理中/失败”，由上层决定是否重试
            return Result.fail("支付处理中或已被处理，请稍后查询状态");
        }

        final Long txnId = txn.getId();
        PaymentPaidEventDTO evt = new PaymentPaidEventDTO();
        evt.setOrderId(orderId);
        evt.setUserId(userId);
        evt.setAmountCents(amountCents);
        evt.setTxnId(txnId);
        evt.setPaidAt(LocalDateTime.now());

        // 事务提交后发布事件，避免“扣款回滚但发了消息”
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { producer.publishPaid(evt); }
            });
        } else {
            producer.publishPaid(evt);
        }

        return Result.ok(txnId);
    }
}