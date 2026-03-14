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

    /** 钱包账户表访问层。 */
    private final WalletAccountMapper accountMapper;
    /** 支付流水表访问层。 */
    private final WalletTxnMapper txnMapper;
    /** 支付成功事件发布器。 */
    private final PaymentEventProducer producer;
    /** 预留 Redis 能力，当前主链路中未直接使用。 */
    private final StringRedisTemplate redisTemplate;

    /** 流水方向：扣款。 */
    private static final int DIR_DEBIT = 1;
    /** 流水状态：成功。 */
    private static final int ST_SUCCESS = 2;
    /** 业务类型：订单支付。 */
    private static final int BIZ_PAY_ORDER = 1;
    /** 支付渠道：余额。 */
    private static final int CH_BALANCE = 1;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Long> payOrder(Long userId, Long orderId, Long amountCents) {
        if (userId == null || orderId == null || amountCents == null || amountCents <= 0) {
            return Result.fail("参数错误");
        }

        // 1) 先做幂等检查，避免同一订单重复扣款。
        WalletTxn exists = txnMapper.findByOrderId(orderId);
        if (exists != null && exists.getStatus() != null && exists.getStatus() == ST_SUCCESS) {
            return Result.fail("paid, txnId = " + exists.getId());
        }

        // 2) 查询钱包账户并检查余额。
        WalletAccount acc = accountMapper.findByUserId(userId);
        if (acc == null) return Result.fail("钱包不存在");
        if (acc.getBalanceCents() == null || acc.getBalanceCents() < amountCents) {
            return Result.fail("余额不足");
        }

        // 3) 使用乐观锁扣减余额，遇到版本冲突时做有限次重试。
        int retry = 3;
        boolean deducted = false;
        while (retry-- > 0) {
            int n = accountMapper.deductBalance(userId, amountCents, acc.getVersion());
            if (n > 0) { deducted = true; break; }
            // 版本冲突后重新读取账户快照，再决定是否继续尝试。
            acc = accountMapper.findByUserId(userId);
            if (acc == null || acc.getBalanceCents() < amountCents) return Result.fail("余额不足");
        }
        if (!deducted) return Result.fail("支付繁忙，请重试");

        // 4) 扣款成功后记录支付流水，后续订单状态回写依赖这条记录。
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
            // 并发插入时由数据库唯一键帮助收敛幂等结果。
            WalletTxn existed = txnMapper.findByOrderId(orderId);
            if (existed != null && existed.getStatus() == ST_SUCCESS) {
                // 成功流水已经存在时，不再重复发布支付成功事件。
                return Result.ok(existed.getId());
            }
            // 其他中间态先统一视为处理中。
            return Result.fail("支付处理中或已被处理，请稍后查询状态");
        }

        final Long txnId = txn.getId();
        PaymentPaidEventDTO evt = new PaymentPaidEventDTO();
        evt.setOrderId(orderId);
        evt.setUserId(userId);
        evt.setAmountCents(amountCents);
        evt.setTxnId(txnId);
        evt.setPaidAt(LocalDateTime.now());

        // 5) 事务提交后再发支付成功事件，避免“扣款回滚但事件已发出”的不一致。
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
