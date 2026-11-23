package com.smartdistribution.analytics.shortage.repository.projection;

import java.math.BigDecimal;

/**
 * 一家供應商最近六個月的表現
 *
 * @param closedLineCount           結束的 PO 明細筆數（收齊與短收結案）
 * @param onTimeLineCount           其中收齊而且不晚於預計到貨日的筆數
 * @param averageLeadTimeDays       收齊的明細從核准到最後一次合格收貨的平均天數；沒有收齊的明細時是 null
 * @param receiptLineCount          收貨明細筆數
 * @param exceptionLineCount        其中有破損或不合格數量的筆數
 * @param overduePurchaseOrderCount 目前已核准還沒收齊、過了預計到貨日的 PO 張數
 */
public record SupplierPerformanceProjection(
        Long supplierId,
        String supplierCode,
        String supplierName,
        int closedLineCount,
        int onTimeLineCount,
        BigDecimal averageLeadTimeDays,
        int receiptLineCount,
        int exceptionLineCount,
        int overduePurchaseOrderCount
) {
}
