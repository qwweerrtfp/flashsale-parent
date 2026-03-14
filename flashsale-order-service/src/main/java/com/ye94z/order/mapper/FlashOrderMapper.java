package com.ye94z.order.mapper;

import com.ye94z.order.entity.FlashOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface FlashOrderMapper {

    /** 新增订单，初始状态一般为 UNPAID。 */
    int insert(FlashOrder order);

    /** 按订单主键查询。 */
    FlashOrder findById(@Param("id") Long id);

    /**
     * 基于旧状态做 CAS 式状态流转。
     * 只有当前状态仍等于 from 时才会更新成功，因此天然具备幂等语义。
     */
    int updateStatusIf(@Param("orderId") Long orderId,
                       @Param("from") int from,
                       @Param("to") int to,
                       @Param("changeTime") LocalDateTime changeTime);

    /** 将未支付订单更新为已支付，并记录支付流水号和支付渠道。 */
    int markPaidIfUnpaid(@Param("orderId") Long orderId,
                         @Param("txnId") Long txnId,
                         @Param("payChannel") byte payChannel);
}
