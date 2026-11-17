package com.smartdistribution.pricing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.entity.CustomerPriceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerPriceResponse(
        Long id,
        Long customerItemMappingId,
        String customerCode,
        String customerItemCode,
        String itemCode,
        UnitOfMeasure pricingUnit,
        BigDecimal unitPriceExcludingTax,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean expired, //截止日已過；過期只看日期，不改狀態
        CustomerPriceStatus status,
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
