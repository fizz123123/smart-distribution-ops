package com.smartdistribution.analytics.cooling.repository.projection;

import java.math.BigDecimal;

/**
 * 一個客戶在期間內新增核准的 SO 張數與接單額
 */
public record CustomerOrdersProjection(
        Long customerId,
        int orderCount,
        BigDecimal amount
) {
}
