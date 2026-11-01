package com.smartdistribution.purchasing.repository;

import java.math.BigDecimal;

/**
 * 依 SO 明細分組加總的 PO 數量（基準單位）
 *
 * @param baseQty      PO 明細的訂購量加總
 * @param acceptedQty  已合格入庫的數量加總
 * @param cancelledQty 取消或短收結案而不再等的數量加總
 */
public record SalesOrderLineQuantityProjection(
        Long salesOrderLineId,
        BigDecimal baseQty,
        BigDecimal acceptedQty,
        BigDecimal cancelledQty
) {

    //未結量 ＝ 訂購量 － 已合格入庫 － 不再等的數量；尚未核准的 PO 入庫量與取消量都是 0
    public BigDecimal openQty() {
        return baseQty.subtract(acceptedQty).subtract(cancelledQty);
    }
}
