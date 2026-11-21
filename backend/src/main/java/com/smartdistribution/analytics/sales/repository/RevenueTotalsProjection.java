package com.smartdistribution.analytics.sales.repository;

import java.math.BigDecimal;

/**
 * 期間內的營收與活躍客戶數
 *
 * @param activeCustomerCount 期間內有 DO 過帳的客戶數
 */
public record RevenueTotalsProjection(
        BigDecimal revenue,
        long activeCustomerCount
) {
}
