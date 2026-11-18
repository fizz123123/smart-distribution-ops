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
        LocalDate orderDate,
        Long responsibleSalesUserId,
        String responsibleSalesName,
        SalesOrderStatus status,
        LocalDate earliestDeliveryDate, //明細中最早的實際交期
        BigDecimal totalAmount, //送審後才有值
        Instant createdAt,
        Long version
) {
}
