package com.smartdistribution.pricing;

import com.smartdistribution.pricing.service.PricePeriods;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核准前提示：核准後實際從哪一天生效、是否已經超過截止日
 */
class PricePeriodsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 10);

    @Test
    void requestedFutureDateNeedsNoNotice() {
        assertThat(PricePeriods.approvalNotices(TODAY.plusDays(5), null, TODAY)).isEmpty();
    }

    @Test
    void blankOrPassedDateStartsOnApprovalDay() {
        assertThat(PricePeriods.approvalNotices(null, null, TODAY))
                .containsExactly("未指定生效日，核准後今天 2026-12-10 起生效");
        assertThat(PricePeriods.approvalNotices(TODAY.minusDays(9), null, TODAY))
                .containsExactly("申請的生效日 2026-12-01 已過，核准後改為今天 2026-12-10 起生效");
    }

    @Test
    void approvalAfterEndDateCannotBeApproved() {
        assertThat(PricePeriods.approvalNotices(TODAY.minusDays(9), TODAY.minusDays(5), TODAY))
                .containsExactly(
                        "申請的生效日 2026-12-01 已過，核准後改為今天 2026-12-10 起生效",
                        "核准後的生效日 2026-12-10 已晚於申請的截止日 2026-12-05，無法核准，請退回後重新申請"
                );
        //截止日就是核准當天還可以核准
        assertThat(PricePeriods.approvalNotices(null, TODAY, TODAY))
                .containsExactly("未指定生效日，核准後今天 2026-12-10 起生效");
    }
}
