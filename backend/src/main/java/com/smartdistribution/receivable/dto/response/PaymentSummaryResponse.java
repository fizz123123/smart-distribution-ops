package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.receivable.entity.PaymentMethod;
import com.smartdistribution.receivable.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentSummaryResponse(
        Long id,
        String paymentNumber,
        PaymentStatus status,
        Long customerId,
        String customerCode,
        String customerName,
        LocalDate receivedDate,
        BigDecimal amount,
        PaymentMethod method,
        String referenceNumber,
        Instant createdAt
) {
}
