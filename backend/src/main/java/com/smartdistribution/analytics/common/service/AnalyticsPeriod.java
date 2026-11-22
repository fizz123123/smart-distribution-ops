package com.smartdistribution.analytics.common.service;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 儀表板的本期與前期，日期都含頭尾：
 * - 沒帶期間：本月 1 日到今天，對上個月 1 日到同一天；上個月沒有那一天就到月底
 * - 帶的是整月：對前面同樣個數的整月，例如 10 月對 9 月、7～9 月對 4～6 月
 * - 其他：對緊接在前、天數相同的區間
 *
 * @param complete 迄日不晚於今天；迄日在未來時，本期還沒過完
 */
public record AnalyticsPeriod(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        boolean complete
) {

    private static final int MAX_DAYS = 366;
    private static final int TREND_MONTHS = 6;

    /**
     * @param from  起日；和迄日要一起帶，或一起不帶
     * @param to    迄日
     * @param today 今天（台北時間）
     * @return 本期與前期
     */
    public static AnalyticsPeriod resolve(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null && to == null) {
            //minusMonths 遇到上個月沒有的日子會自動取月底，例如 3/31 減一個月是 2/28
            return new AnalyticsPeriod(
                    today.withDayOfMonth(1),
                    today,
                    today.withDayOfMonth(1).minusMonths(1),
                    today.minusMonths(1),
                    true
            );
        }
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.ANALYTICS_PERIOD_INVALID);
        }

        boolean complete = !to.isAfter(today);
        if (isWholeMonths(from, to)) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
            return new AnalyticsPeriod(from, to, from.minusMonths(months), from.minusDays(1), complete);
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new AnalyticsPeriod(from, to, from.minusDays(days), from.minusDays(1), complete);
    }

    /**
     * 交易降溫的分析基準日；庫存、應收沒有歷史快照，所以只有交易降溫能指定過去的日期
     *
     * @param asOf  基準日；沒帶時是今天
     * @param today 今天（台北時間）
     * @return 基準日；是未來時回 ANALYTICS_PERIOD_INVALID
     */
    public static LocalDate resolveAsOf(LocalDate asOf, LocalDate today) {
        if (asOf == null) {
            return today;
        }
        if (asOf.isAfter(today)) {
            throw new BusinessException(ErrorCode.ANALYTICS_PERIOD_INVALID);
        }
        return asOf;
    }

    /**
     * 近六個月趨勢：今天所在的月份往前共六個自然月，含本月，由舊到新
     *
     * @param today 今天（台北時間）
     * @return 六個月份
     */
    public static List<YearMonth> recentMonths(LocalDate today) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        for (int offset = TREND_MONTHS - 1; offset >= 0; offset--) {
            months.add(current.minusMonths(offset));
        }

        return months;
    }

    //起日是 1 日、迄日是月底
    private static boolean isWholeMonths(LocalDate from, LocalDate to) {
        return from.getDayOfMonth() == 1 && to.getDayOfMonth() == to.lengthOfMonth();
    }
}
