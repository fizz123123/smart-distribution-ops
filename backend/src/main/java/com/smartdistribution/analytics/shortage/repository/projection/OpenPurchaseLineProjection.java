package com.smartdistribution.analytics.shortage.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一筆還沒結的 PO 明細，包含一般補庫的 PO；不含單價，倉管也看得到缺貨分析
 *
 * @param status  PO 表頭狀態，例如 DRAFT、APPROVED；分類見 PurchaseStatusRules
 * @param openQty 還沒到的數量 ＝ 訂購 － 合格入庫 － 已取消，庫存基準單位
 */
public record OpenPurchaseLineProjection(
        Long companyItemId,
        Long purchaseOrderId,
        String poNumber,
        String status,
        LocalDate expectedArrivalDate,
        BigDecimal openQty
) {
}
