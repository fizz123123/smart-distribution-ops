package com.smartdistribution.analytics.sales.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已核准 SO 中還有未出貨量的一筆明細
 *
 * @param remainingQuantity 訂購數量 － 已核准取消 － 已過帳出貨，訂購單位
 * @param amount            未出貨數量 × SO 明細單價
 */
public record BacklogLineProjection(
        Long salesOrderLineId,
        Long salesOrderId,
        String soNumber,
        Integer lineNo,
        Long customerId,
        Long companyItemId,
        LocalDate deliveryDate,
        BigDecimal remainingQuantity,
        BigDecimal amount
) {
}
