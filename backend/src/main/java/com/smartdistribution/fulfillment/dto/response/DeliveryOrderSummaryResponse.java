package com.smartdistribution.fulfillment.dto.response;

import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;

import java.time.Instant;
import java.time.LocalDate;

public record DeliveryOrderSummaryResponse(
        Long id,
        String doNumber,
        DeliveryOrderStatus status,
        Long salesOrderId,
        String soNumber,
        String customerCode,
        String customerName,
        LocalDate plannedShipmentDate,
        LocalDate actualShipmentDate,
        String createdByName,
        Instant createdAt
) {
}
