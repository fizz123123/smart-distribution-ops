package com.smartdistribution.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * 應收的金額合計；應收總額與未收只算正式應收，待確認的草稿另外計算
 *
 * @param totalAmount       正式應收的含稅總額
 * @param outstandingAmount 正式應收的未收餘額
 * @param pendingCount      待確認的草稿筆數
 * @param pendingAmount     待確認的草稿含稅金額
 */
public record ReceivableTotalsProjection(
        BigDecimal totalAmount,
        BigDecimal outstandingAmount,
        int pendingCount,
        BigDecimal pendingAmount
) {
}
