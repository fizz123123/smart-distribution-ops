package com.smartdistribution.sales.service;

import java.math.BigDecimal;

/**
 * 給採購模組的 SO 明細需求，採購用它檢查與計算「還能規劃多少」
 *
 * @param approved     SO 是否為已核准；只有已核准的 SO 能關聯 PO
 * @param remainingQty 剩餘需求 ＝ 有效訂購量 － 已出貨量（基準單位）；SO 不是已核准時為 0
 * @param reservedQty  目前有效預留（基準單位）
 */
public record SalesOrderLineDemand(
        Long salesOrderLineId,
        Long salesOrderId,
        String salesOrderNumber,
        Integer lineNo,
        String customerItemCode,
        Long companyItemId,
        boolean approved,
        BigDecimal remainingQty,
        BigDecimal reservedQty
) {
}
