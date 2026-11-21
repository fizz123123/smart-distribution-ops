package com.smartdistribution.analytics.sales.repository;

import java.math.BigDecimal;

/**
 * 期間內核准的 SO：接單額、張數與下單客戶數
 */
public record NewOrderTotalsProjection(
        BigDecimal amount,
        long orderCount,
        long customerCount
) {
}
