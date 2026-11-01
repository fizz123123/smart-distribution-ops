package com.smartdistribution.purchasing.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record GoodsReceiptSummaryResponse(
        Long id,
        String receiptNumber,
        Long purchaseOrderId,
        String poNumber,
        String supplierName,
        LocalDate receivedDate,
        String postedByName,
        Instant postedAt
) {
}
