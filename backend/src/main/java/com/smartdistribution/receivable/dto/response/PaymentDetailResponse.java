package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.receivable.entity.PaymentMethod;
import com.smartdistribution.receivable.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 收款詳情：金額是含稅 TWD；作廢後核銷明細仍列出，但已不算進應收的已收金額
 */
public record PaymentDetailResponse(
        Long id,
        String paymentNumber,
        String clientRequestId,
        PaymentStatus status,
        CustomerRef customer,
        LocalDate receivedDate,
        BigDecimal amount,
        PaymentMethod method,
        String referenceNumber,
        UserRef createdBy,
        Voidance voidance,
        Instant createdAt,
        Long version,
        List<Allocation> allocations
) {

    public record CustomerRef(
            Long id,
            String customerCode,
            String name
    ) {
    }

    public record UserRef(
            Long id,
            String name
    ) {
    }

    //作廢的紀錄；沒有作廢時整個是 null
    public record Voidance(
            String reason,
            UserRef by,
            Instant at
    ) {
    }

    public record Allocation(
            Long receivableId,
            String receivableNumber,
            String doNumber,
            BigDecimal allocatedAmount
    ) {
    }
}
