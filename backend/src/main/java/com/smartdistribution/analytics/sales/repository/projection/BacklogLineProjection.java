package com.smartdistribution.analytics.sales.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已核准 SO 中還有未出貨量的一筆明細
 *
 * @param remainingQuantity 訂購數量 － 已核准取消 － 已過帳出貨，訂購單位
 * @param unit              訂購單位的代碼，例如 BOX
 * @param amount            未出貨數量 × SO 明細單價
 */
public record BacklogLineProjection(
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
        BigDecimal amount
) {

    /**
     * @return 交期早於今天還沒出貨，就是逾期未出貨
     */
    public boolean isOverdue(LocalDate today) {
        return deliveryDate != null && deliveryDate.isBefore(today);
    }
}
