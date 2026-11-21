package com.smartdistribution.analytics.sales.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 核准取消的一筆來源：已核准取消申請的一行
 *
 * @param unit 訂購單位的代碼
 */
public record CancellationSourceResponse(
        LocalDate approvedDate,
        Long cancellationId,
        String cancellationNumber,
        Long salesOrderId,
        String soNumber,
        Long customerId,
        String customerCode,
        String customerName,
        Long companyItemId,
        String itemCode,
        String itemName,
        BigDecimal quantity,
        String unit,
        BigDecimal amount
) {
}
