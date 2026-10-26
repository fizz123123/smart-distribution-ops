package com.smartdistribution.sales.repository;

import java.math.BigDecimal;

/**
 * 每筆 SO 明細已核准的取消基準數量加總
 */
public record CancelledQuantityProjection(
        Long salesOrderLineId,
        BigDecimal cancelledQty
) {
}
