package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.receivable.service.DueStatus;

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
        DueStatus dueStatus,
        String externalInvoiceNumber,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
