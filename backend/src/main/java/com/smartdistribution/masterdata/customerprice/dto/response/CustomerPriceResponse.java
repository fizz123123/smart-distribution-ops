package com.smartdistribution.masterdata.customerprice.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerPriceResponse(
        Long id,
        Long customerItemMappingId,
        String customerCode,
        String customerItemCode,
        String itemCode,
        UnitOfMeasure pricingUnit,
        BigDecimal unitPriceExcludingTax,
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
