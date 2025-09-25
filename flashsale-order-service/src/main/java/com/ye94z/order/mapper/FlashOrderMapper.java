package com.ye94z.order.mapper;

import com.ye94z.order.entity.FlashOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface FlashOrderMapper {

    /**
     * 新增订单（UNPAID）
     */
    int insert(FlashOrder order);

    /**
     * 按主键查
     */
    FlashOrder findById(@Param("id") Long id);

    /**
     * 基于旧状态的 CAS 更新状态（幂等）
     * from -> to；根据目标状态写入对应时间字段（pay/fulfill/close）
     */
    int updateStatusIf(@Param("orderId") Long orderId,
                       @Param("from") int from,
                       @Param("to") int to,
                       @Param("changeTime") LocalDateTime changeTime);

    int markPaidIfUnpaid(@Param("orderId") Long orderId,
                         @Param("txnId") Long txnId,
                         @Param("payChannel") byte payChannel);
}