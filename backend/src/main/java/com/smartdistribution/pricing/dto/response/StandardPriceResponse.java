package com.smartdistribution.pricing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.entity.PriceStatus;

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
        boolean expired, //截止日已過；過期只看日期，不改狀態
        PriceStatus status,
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
