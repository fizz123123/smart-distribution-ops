package com.smartdistribution.analytics;

import com.smartdistribution.analytics.dto.response.MetricComparison;
import com.smartdistribution.analytics.service.AnalyticsNote;
import com.smartdistribution.analytics.service.MetricChanges;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前期是 0 時不算變化率，避免除以 0 或出現無限大
 */
class MetricChangesTest {

    @Test
    void changeRateIsCalculatedWhenPreviousIsPositive() {
        MetricComparison comparison = MetricChanges.compare(new BigDecimal("1250"), new BigDecimal("1000"));

        assertThat(comparison.change()).isEqualByComparingTo("250");
        assertThat(comparison.changeRate()).isEqualByComparingTo("0.25");
        assertThat(comparison.note()).isNull();
    }

    @Test
    void noChangeRateWithoutPriorBaseline() {
        MetricComparison comparison = MetricChanges.compare(new BigDecimal("2000"), BigDecimal.ZERO);

        assertThat(comparison.change()).isEqualByComparingTo("2000");
        assertThat(comparison.changeRate()).isNull();
        assertThat(comparison.note()).isEqualTo(AnalyticsNote.NO_PRIOR_BASELINE);
    }

    @Test
    void missingValueMeansInsufficientData() {
        MetricComparison comparison = MetricChanges.compare(new BigDecimal("100"), null);

        assertThat(comparison.change()).isNull();
        assertThat(comparison.note()).isEqualTo(AnalyticsNote.INSUFFICIENT_DATA);
    }
}
