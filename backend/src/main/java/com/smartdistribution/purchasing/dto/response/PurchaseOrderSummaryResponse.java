package com.smartdistribution.purchasing.dto.response;

import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PurchaseOrderSummaryResponse(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierCode,
        String supplierName,
        PurchaseOrderStatus status,
        LocalDate expectedArrivalDate,
        BigDecimal untaxedAmount,
        Long createdBy,
        String createdByName,
        Instant createdAt,
        Long version
) {
}
