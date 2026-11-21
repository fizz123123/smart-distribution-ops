package com.smartdistribution.analytics.dto.response;

import com.smartdistribution.analytics.service.AgingBucket;
import com.smartdistribution.receivable.service.DueStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 應收與逾期；金額都是含稅，以 asOf（今天）為準
 *
 * @param aging          帳齡，固定列出每一組，沒有應收的組金額為 0
 * @param dueReceivables 即將到期與逾期的應收，依到期日排序
 */
public record ReceivableAnalysisResponse(
        LocalDate asOf,
        Summary summary,
        List<AgingGroup> aging,
        List<DueReceivable> dueReceivables
) {

    /**
     * @param totalAmount       正式應收的含稅總額，不含待確認的草稿
     * @param outstandingAmount 正式應收的未收餘額
     * @param dueSoon           今天到第 7 天到期，包含草稿
     * @param overdue           到期日早於今天，包含草稿
     */
    public record Summary(
            BigDecimal totalAmount,
            BigDecimal outstandingAmount,
            int pendingConfirmationCount,
            BigDecimal pendingConfirmationAmount,
            DueGroup dueSoon,
            DueGroup overdue
    ) {
    }

    /**
     * @param unconfirmedCount 其中還沒確認的草稿筆數
     */
    public record DueGroup(
            int count,
            BigDecimal outstandingAmount,
            int unconfirmedCount
    ) {
    }

    public record AgingGroup(
            AgingBucket bucket,
            int count,
            BigDecimal outstandingAmount
    ) {
    }

    /**
     * @param status      應收狀態，例如 PENDING_CONFIRMATION 代表還沒確認的草稿
     * @param daysPastDue 逾期天數；還沒到期是 0
     */
    public record DueReceivable(
            Long receivableId,
            String receivableNumber,
            String status,
            DueStatus dueStatus,
            Long customerId,
            String customerCode,
            String customerName,
            Long salesOrderId,
            String soNumber,
            Long deliveryOrderId,
            String doNumber,
            LocalDate dueDate,
            long daysPastDue,
            BigDecimal outstandingAmount
    ) {
    }
}
