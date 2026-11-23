package com.smartdistribution.analytics.sales.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 營收的一筆來源：一筆應收明細，也就是一張已過帳 DO 的一行
 *
 * @param unit 訂購單位的代碼，例如 BOX
 */
public record RevenueSourceProjection(
        LocalDate shipmentDate,
        Long receivableId,
        String receivableNumber,
        Long deliveryOrderId,
        String doNumber,
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
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
