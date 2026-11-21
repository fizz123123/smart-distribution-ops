package com.smartdistribution.analytics.service;

import com.smartdistribution.analytics.dto.response.MetricComparison;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 本期與前期的比較：變化額 ＝ 本期 － 前期；前期大於 0 才算變化率，避免除以 0 或出現無限大
 */
public final class MetricChanges {

    private MetricChanges() {
    }

    /**
     * @param current  本期；null 代表算不出來，例如沒有訂單時的平均訂單金額
     * @param previous 前期；null 的意思同上
     * @return 比較值；變化率是小數，0.25 代表 25%
     */
    public static MetricComparison compare(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return new MetricComparison(current, previous, null, null, AnalyticsNote.INSUFFICIENT_DATA);
        }

        BigDecimal change = current.subtract(previous);
        if (previous.signum() <= 0) {
            return new MetricComparison(current, previous, change, null, AnalyticsNote.NO_PRIOR_BASELINE);
        }

        return new MetricComparison(current, previous, change, change.divide(previous, 4, RoundingMode.HALF_UP), null);
    }

    public static MetricComparison compare(long current, long previous) {
        return compare(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }
}
