package com.smartdistribution.analytics.cooling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 交易降溫的計算規則，只有 static 方法、不查資料庫
 */
public final class CoolingRules {

    //至少要有幾個不同的交易日才算週期；兩個交易日只有一個間隔，看不出節奏
    public static final int MIN_TRADE_DAYS = 3;

    //平均月營收看基準日往前幾個月
    public static final int REVENUE_MONTHS = 6;

    private CoolingRules() {
    }

    /**
     * 平均交易週期 ＝（最後一個 － 第一個交易日）÷（交易日數 － 1），等於相鄰間隔的平均
     *
     * @return 天數，小數 1 位；交易日不足 3 個時是 null
     */
    public static BigDecimal averageCycleDays(LocalDate firstTradeDate, LocalDate lastTradeDate, int tradeDayCount) {
        if (tradeDayCount < MIN_TRADE_DAYS) {
            return null;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(firstTradeDate, lastTradeDate))
                .divide(BigDecimal.valueOf(tradeDayCount - 1L), 1, RoundingMode.HALF_UP);
    }

    /**
     * 比值 ＝ 距上次交易天數 ÷ 平均交易週期；用沒有四捨五入的週期算，只在最後取小數 2 位
     *
     * @return 比值；交易日不足 3 個時是 null
     */
    public static BigDecimal cycleRatio(
            long daysSinceLastTrade,
            LocalDate firstTradeDate,
            LocalDate lastTradeDate,
            int tradeDayCount
    ) {
        if (tradeDayCount < MIN_TRADE_DAYS) {
            return null;
        }
        long span = ChronoUnit.DAYS.between(firstTradeDate, lastTradeDate);
        return BigDecimal.valueOf(daysSinceLastTrade * (tradeDayCount - 1L))
                .divide(BigDecimal.valueOf(span), 2, RoundingMode.HALF_UP);
    }

    /**
     * @param revenue 基準日往前六個月的營收
     * @return 平均月營收，小數 2 位；只用來排序，不影響風險等級
     */
    public static BigDecimal averageMonthlyRevenue(BigDecimal revenue) {
        return revenue.divide(BigDecimal.valueOf(REVENUE_MONTHS), 2, RoundingMode.HALF_UP);
    }
}
