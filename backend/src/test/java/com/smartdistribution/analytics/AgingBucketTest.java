package com.smartdistribution.analytics;

import com.smartdistribution.analytics.service.AgingBucket;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 帳齡分組的邊界：今天到期還不算逾期，逾期第 30、60、90 天各自落在前一組
 */
class AgingBucketTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Test
    void bucketsFollowDaysPastDue() {
        assertThat(AgingBucket.of(TODAY.plusDays(10), TODAY)).isEqualTo(AgingBucket.NOT_DUE);
        assertThat(AgingBucket.of(TODAY, TODAY)).isEqualTo(AgingBucket.NOT_DUE);
        assertThat(AgingBucket.of(TODAY.minusDays(1), TODAY)).isEqualTo(AgingBucket.DAYS_1_30);
        assertThat(AgingBucket.of(TODAY.minusDays(30), TODAY)).isEqualTo(AgingBucket.DAYS_1_30);
        assertThat(AgingBucket.of(TODAY.minusDays(31), TODAY)).isEqualTo(AgingBucket.DAYS_31_60);
        assertThat(AgingBucket.of(TODAY.minusDays(60), TODAY)).isEqualTo(AgingBucket.DAYS_31_60);
        assertThat(AgingBucket.of(TODAY.minusDays(61), TODAY)).isEqualTo(AgingBucket.DAYS_61_90);
        assertThat(AgingBucket.of(TODAY.minusDays(90), TODAY)).isEqualTo(AgingBucket.DAYS_61_90);
        assertThat(AgingBucket.of(TODAY.minusDays(91), TODAY)).isEqualTo(AgingBucket.OVER_90);
    }
}
