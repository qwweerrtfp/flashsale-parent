package com.ye94z.payment.mapper;

import com.ye94z.payment.entity.WalletAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletAccountMapper {
    /** 按用户 ID 查询钱包账户。 */
    WalletAccount findByUserId(@Param("userId") Long userId);

    /**
     * 乐观锁扣减余额。
     * 只有 version 命中且余额足够时，更新才会成功。
     */
    int deductBalance(@Param("userId") Long userId,
                      @Param("amount") Long amount,
                      @Param("version") Integer version);
}
