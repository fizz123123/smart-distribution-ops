package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.receivable.entity.ReceivableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ReceivableSummaryResponse(
        Long id,
        String receivableNumber,
        ReceivableStatus status,
        Long customerId,
        String customerCode,
        String customerName,
        String doNumber,
        String soNumber,
        LocalDate actualShipmentDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
