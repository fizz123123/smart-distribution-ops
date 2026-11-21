package com.smartdistribution.analytics.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 還沒收清的一筆應收，包含待確認的草稿
 *
 * @param status 應收狀態的代碼，例如 PENDING_CONFIRMATION；分析模組不引用應收的列舉
 */
public record OpenReceivableProjection(
        Long receivableId,
        String receivableNumber,
        String status,
        Long customerId,
        String customerCode,
        String customerName,
        Long salesOrderId,
        String soNumber,
        Long deliveryOrderId,
        String doNumber,
        LocalDate dueDate,
        BigDecimal outstandingAmount
) {

    public boolean isUnconfirmed() {
        return "PENDING_CONFIRMATION".equals(status);
    }
}
