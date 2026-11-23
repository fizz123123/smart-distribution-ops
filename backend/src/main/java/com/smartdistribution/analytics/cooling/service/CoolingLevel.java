package com.smartdistribution.analytics.cooling.service;

import java.math.BigDecimal;

/**
 * 交易降溫的風險等級：距上次交易天數是平均交易週期的幾倍
 */
public enum CoolingLevel {
    HIGH,   //超過 2 倍
    MEDIUM; //超過 1.5 倍，最多 2 倍

    private static final BigDecimal HIGH_RATIO = new BigDecimal("2.0");
    private static final BigDecimal MEDIUM_RATIO = new BigDecimal("1.5");

    /**
     * @param cycleRatio 距上次交易天數 ÷ 平均交易週期，小數 2 位；畫面看到的比值和等級一致
     * @return 風險等級；比值算不出來或沒有超過 1.5 倍時是 null
     */
    public static CoolingLevel of(BigDecimal cycleRatio) {
        if (cycleRatio == null) {
            return null;
        }
        if (cycleRatio.compareTo(HIGH_RATIO) > 0) {
            return HIGH;
        }
        if (cycleRatio.compareTo(MEDIUM_RATIO) > 0) {
            return MEDIUM;
        }
        return null;
    }
}
