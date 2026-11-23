package com.smartdistribution.analytics.sales.dto.response;

import com.smartdistribution.analytics.common.service.SupplyStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已接單未出貨的一筆來源：一筆還有未出貨量的 SO 明細
 *
 * @param remainingQuantity 未出貨數量，訂購單位
 * @param unit              訂購單位的代碼，例如 BOX
 * @param amount            未出貨數量 × SO 明細單價
 * @param overdue           交期早於今天
 */
public record BacklogSourceResponse(
        Long salesOrderLineId,
        Long salesOrderId,
        String soNumber,
        Integer lineNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long companyItemId,
        String itemCode,
        String itemName,
        LocalDate deliveryDate,
        BigDecimal remainingQuantity,
        String unit,
        BigDecimal amount,
        SupplyStatus supplyStatus,
        boolean overdue
) {
}
