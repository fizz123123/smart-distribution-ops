package com.smartdistribution.analytics.repository.projection;

/**
 * 期間內出貨的 DO 明細筆數，以及其中實際出貨日不晚於 SO 明細交期的筆數
 */
public record OnTimeDeliveryProjection(
        long shippedLineCount,
        long onTimeLineCount
) {
}
