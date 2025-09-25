package com.ye94z.payment.mapper;

import com.ye94z.payment.entity.WalletTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTxnMapper {
    int insert(WalletTxn txn);
    WalletTxn findByOrderId(@Param("orderId") Long orderId);
}