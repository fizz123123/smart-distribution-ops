package com.smartdistribution.analytics.dto.response;

import com.smartdistribution.analytics.service.AnalyticsNote;

import java.math.BigDecimal;

/**
 * 本期與前期的比較值
 *
 * @param changeRate 變化率，0.25 代表 25%；前期是 0 或算不出來時為 null，原因寫在 note
 */
public record MetricComparison(
        BigDecimal current,
        BigDecimal previous,
        BigDecimal change,
        BigDecimal changeRate,
        AnalyticsNote note
) {
}
