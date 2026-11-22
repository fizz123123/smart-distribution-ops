package com.smartdistribution.analytics.common;

import com.smartdistribution.analytics.common.service.AnalyticsPeriod;
import com.smartdistribution.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本期與前期怎麼決定：預設本月至今對上月同期、整月對整月、其他用等長區間
 */
class AnalyticsPeriodTest {

    @Test
    void defaultIsMonthToDateAgainstSameDaysOfPreviousMonth() {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(null, null, LocalDate.of(2026, 11, 21));

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(period.previousFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(period.previousTo()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(period.complete()).isTrue();
    }

    @Test
    void previousMonthWithoutThatDayEndsAtMonthEnd() {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(null, null, LocalDate.of(2026, 3, 31));

        assertThat(period.previousFrom()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(period.previousTo()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void wholeMonthsAreComparedWithTheSameNumberOfWholeMonths() {
        LocalDate today = LocalDate.of(2026, 11, 21);

        AnalyticsPeriod october = AnalyticsPeriod.resolve(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), today);
        assertThat(october.previousFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(october.previousTo()).isEqualTo(LocalDate.of(2026, 9, 30));

        AnalyticsPeriod quarter = AnalyticsPeriod.resolve(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), today);
        assertThat(quarter.previousFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(quarter.previousTo()).isEqualTo(LocalDate.of(2026, 6, 30));

        //跨年
        AnalyticsPeriod january = AnalyticsPeriod.resolve(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), today);
        assertThat(january.previousFrom()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(january.previousTo()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void otherPeriodsAreComparedWithTheSameNumberOfDaysBefore() {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 18),
                LocalDate.of(2026, 11, 21)
        );

        assertThat(period.previousFrom()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(period.previousTo()).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    void periodEndingAfterTodayIsNotComplete() {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 11, 21)
        );

        assertThat(period.complete()).isFalse();
    }

    @Test
    void invalidPeriodsAreRejected() {
        LocalDate today = LocalDate.of(2026, 11, 21);

        assertThatThrownBy(() -> AnalyticsPeriod.resolve(LocalDate.of(2026, 11, 1), null, today))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AnalyticsPeriod.resolve(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), today))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AnalyticsPeriod.resolve(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), today))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recentMonthsAreSixMonthsEndingWithThisMonth() {
        assertThat(AnalyticsPeriod.recentMonths(LocalDate.of(2026, 2, 10))).containsExactly(
                YearMonth.of(2025, 9),
                YearMonth.of(2025, 10),
                YearMonth.of(2025, 11),
                YearMonth.of(2025, 12),
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 2)
        );
    }

    @Test
    void asOfDefaultsToTodayAndCannotBeInTheFuture() {
        LocalDate today = LocalDate.of(2026, 11, 23);

        assertThat(AnalyticsPeriod.resolveAsOf(null, today)).isEqualTo(today);
        assertThat(AnalyticsPeriod.resolveAsOf(today.minusDays(30), today)).isEqualTo(today.minusDays(30));
        assertThatThrownBy(() -> AnalyticsPeriod.resolveAsOf(today.plusDays(1), today))
                .isInstanceOf(BusinessException.class);
    }
}
