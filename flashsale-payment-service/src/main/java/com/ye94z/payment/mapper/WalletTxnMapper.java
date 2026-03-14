package com.ye94z.payment.mapper;

import com.ye94z.payment.entity.WalletTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTxnMapper {
    /** 新增支付流水。 */
    int insert(WalletTxn txn);
    /** 按订单号查询支付流水，用于幂等判断。 */
    WalletTxn findByOrderId(@Param("orderId") Long orderId);
}
