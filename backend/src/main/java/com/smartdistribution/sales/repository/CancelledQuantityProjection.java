package com.smartdistribution.sales.repository;

import java.math.BigDecimal;

/**
 * 每筆 SO 明細已核准的取消數量加總
 *
 * @param cancelledQty      基準單位，算剩餘需求、尚待規劃用
 * @param cancelledQuantity 訂購單位，算有效金額用
 */
public record CancelledQuantityProjection(
        Long salesOrderLineId,
        BigDecimal cancelledQty,
        BigDecimal cancelledQuantity
) {
}
