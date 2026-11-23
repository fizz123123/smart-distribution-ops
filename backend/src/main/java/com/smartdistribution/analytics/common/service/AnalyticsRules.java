package com.smartdistribution.analytics.common.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 各分析共用的計算規則，只有 static 方法、不查資料庫
 */
public final class AnalyticsRules {

    private AnalyticsRules() {
    }

    /**
     * 準時交貨率、準時到貨率、收貨異常率共用，三個比例的算法只有一份
     *
     * @return part ÷ total，小數 4 位，例如 0.25 代表 25%；total 是 0 時算不出來，回傳 null
     */
    public static BigDecimal ratio(long part, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}
