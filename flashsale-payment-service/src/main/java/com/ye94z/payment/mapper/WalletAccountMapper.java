package com.ye94z.payment.mapper;

import com.ye94z.payment.entity.WalletAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletAccountMapper {
    WalletAccount findByUserId(@Param("userId") Long userId);

    /**
     * 乐观扣减余额：命中 version 时才扣减成功
     * @return 影响行数，1 表示成功；0 表示失败（余额不足或版本冲突）
     */
    int deductBalance(@Param("userId") Long userId,
                      @Param("amount") Long amount,
                      @Param("version") Integer version);
}