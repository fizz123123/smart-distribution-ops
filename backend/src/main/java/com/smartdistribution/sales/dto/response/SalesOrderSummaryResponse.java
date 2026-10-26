package com.smartdistribution.sales.dto.response;

import com.smartdistribution.sales.entity.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SalesOrderSummaryResponse(
        Long id,
        String soNumber,
        Long customerId,
        String customerCode,
        String customerName,
        String customerOrderNumber,
        Long responsibleSalesUserId,
        String responsibleSalesName,
        SalesOrderStatus status,
        LocalDate requestedDeliveryDate,
        BigDecimal totalAmount, //送審後才有值
        Instant createdAt,
        Long version
) {
}
