package com.smartdistribution.analytics.sales.repository.projection;

import java.math.BigDecimal;

/**
 * 某個客戶或料號在本期與前期的營收
 *
 * @param code 客戶代碼或料號
 */
public record ContributionProjection(
        Long id,
        String code,
        String name,
        BigDecimal currentAmount,
        BigDecimal previousAmount
) {
}
