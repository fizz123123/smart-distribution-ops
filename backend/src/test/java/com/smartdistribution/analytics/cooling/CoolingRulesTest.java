package com.smartdistribution.analytics.cooling;

import com.smartdistribution.analytics.cooling.service.CoolingLevel;
import com.smartdistribution.analytics.cooling.service.CoolingRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平均交易週期、比值與等級的邊界：1.5 倍不列，超過 1.5 到 2.0 是 MEDIUM，超過 2.0 是 HIGH
 */
class CoolingRulesTest {

    //三個交易日：1/1、1/11、1/21，平均週期 10 天
    private static final LocalDate FIRST = LocalDate.of(2026, 1, 1);
    private static final LocalDate LAST = LocalDate.of(2026, 1, 21);

    @Test
    void averageCycleNeedsAtLeastThreeTradeDays() {
        assertThat(CoolingRules.averageCycleDays(FIRST, LAST, 3)).isEqualByComparingTo("10.0");
        assertThat(CoolingRules.averageCycleDays(FIRST, LAST, 2)).isNull();
        assertThat(CoolingRules.cycleRatio(30, FIRST, LAST, 2)).isNull();
    }

    @Test
    void levelFollowsCycleRatio() {
        assertThat(level(15)).isNull();
        assertThat(level(16)).isEqualTo(CoolingLevel.MEDIUM);
        assertThat(level(20)).isEqualTo(CoolingLevel.MEDIUM);
        assertThat(level(21)).isEqualTo(CoolingLevel.HIGH);
        assertThat(CoolingRules.cycleRatio(21, FIRST, LAST, 3)).isEqualByComparingTo("2.10");
        assertThat(CoolingLevel.of(null)).isNull();
    }

    @Test
    void averageMonthlyRevenueIsSixMonthsDividedBySix() {
        assertThat(CoolingRules.averageMonthlyRevenue(new BigDecimal("600"))).isEqualByComparingTo("100");
        assertThat(CoolingRules.averageMonthlyRevenue(new BigDecimal("100"))).isEqualByComparingTo("16.67");
    }

    //距上次交易幾天時的等級
    private static CoolingLevel level(long daysSinceLastTrade) {
        return CoolingLevel.of(CoolingRules.cycleRatio(daysSinceLastTrade, FIRST, LAST, 3));
    }
}
