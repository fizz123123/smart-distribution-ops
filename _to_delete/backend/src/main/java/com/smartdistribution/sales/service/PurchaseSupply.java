package com.smartdistribution.sales.service;

import java.math.BigDecimal;

/**
 * 一筆 SO 明細的採購供應數量，以庫存基準單位表示
 *
 * @param plannedNotOrderedQty 已規劃未下單：關聯這筆明細、尚未核准的 PO 數量
 * @param inTransitQty         採購在途：關聯這筆明細、已核准但還沒入庫的 PO 數量
 */
public record PurchaseSupply(
        BigDecimal plannedNotOrderedQty,
        BigDecimal inTransitQty
) {

    public static final PurchaseSupply NONE = new PurchaseSupply(BigDecimal.ZERO, BigDecimal.ZERO);
}
