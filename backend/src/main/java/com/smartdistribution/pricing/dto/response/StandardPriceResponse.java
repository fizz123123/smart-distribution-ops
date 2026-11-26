package com.smartdistribution.pricing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.service.PriceEffectiveState;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StandardPriceResponse(
        Long id,
        Long companyItemId,
        String itemCode,
        String itemName,
        UnitOfMeasure pricingUnit,
        BigDecimal unitPriceExcludingTax,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        PriceStatus status,
        PriceEffectiveState effectiveState, //依今天算出的效期狀態；只有 ACTIVE、REPLACED 有值
        Long createdBy,
        Instant createdAt,
        Long reviewedBy,
        Instant reviewedAt,
        Long revokedBy,
        Instant revokedAt,
        String revokeReason,
        Long version
) {
}
