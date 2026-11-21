package com.smartdistribution.analytics.sales.repository.projection;

import java.math.BigDecimal;

/**
 * 某個鍵對應的金額，例如「2026-11 → 本月營收」；形狀相同的查詢結果共用這一個
 */
public record AmountByKeyProjection(
        String key,
        BigDecimal amount
) {
}
