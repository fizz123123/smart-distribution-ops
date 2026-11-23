package com.smartdistribution.analytics.common;

import com.smartdistribution.analytics.common.service.AnalyticsRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 準時交貨率、準時到貨率、收貨異常率共用的比例
 */
class AnalyticsRulesTest {

    @Test
    void ratioIsNullWithoutData() {
        assertThat(AnalyticsRules.ratio(1, 3)).isEqualByComparingTo("0.3333");
        assertThat(AnalyticsRules.ratio(3, 4)).isEqualByComparingTo("0.75");
        assertThat(AnalyticsRules.ratio(0, 4)).isEqualByComparingTo("0");
        assertThat(AnalyticsRules.ratio(0, 0)).isNull();
    }
}
