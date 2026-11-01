package com.smartdistribution.inventory.service;

import java.math.BigDecimal;

/**
 * PO 驗收時，採購模組交給庫存模組的「一筆合格入庫」，
 * 是 {@link InventoryService#receiveForPurchaseOrder} 的輸入
 *
 * @param goodsReceiptLineId 入庫異動與預留事件的來源
 * @param companyItemId      要鎖定與增加庫存的料號
 * @param acceptedBaseQty    合格數量（基準單位），只有合格的才入庫
 * @param salesOrderLineId   關聯 PO 才有值：要補足預留的 SO 明細；一般補庫為 null
 * @param remainingQty       關聯 SO 明細的剩餘需求（有效訂購量 － 已出貨量）；一般補庫為 0
 */
public record ReceiptLine(
        Long goodsReceiptLineId,
        Long companyItemId,
        BigDecimal acceptedBaseQty,
        Long salesOrderLineId,
        BigDecimal remainingQty
) {
}
