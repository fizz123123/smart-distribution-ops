package com.smartdistribution.analytics.dto.response;

import com.smartdistribution.analytics.service.AnalyticsPeriod;

import java.time.LocalDate;

/**
 * @param periodComplete 迄日在未來時為 false，前端標示「截至今日」
 */
public record AnalyticsPeriodResponse(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        boolean periodComplete
) {

    public static AnalyticsPeriodResponse from(AnalyticsPeriod period) {
        return new AnalyticsPeriodResponse(
                period.from(),
                period.to(),
                period.previousFrom(),
                period.previousTo(),
                period.complete()
        );
    }
}
