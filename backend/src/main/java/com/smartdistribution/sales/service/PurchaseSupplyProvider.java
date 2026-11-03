package com.smartdistribution.sales.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * SO 詳情需要的採購供應數量，由採購模組實作
 *
 * <p>採購模組建 PO 時要讀 SO 的需求，SO 詳情又要顯示 PO 的進度；兩邊直接互相呼叫會循環依賴，
 * 所以由銷售模組定義它需要什麼，採購模組去實作，模組依賴只剩採購 → 銷售一個方向
 */
public interface PurchaseSupplyProvider {

    /**
     * @return SO 明細 ID → 採購供應數量；沒有任何 PO 關聯的明細不會出現在結果中
     */
    Map<Long, PurchaseSupply> findBySalesOrderLines(Collection<Long> salesOrderLineIds);

    /**
     * 一筆 SO 明細的採購供應數量，以庫存基準單位表示
     *
     * @param plannedNotOrderedQty 已規劃未下單：關聯這筆明細、尚未核准的 PO 數量
     * @param inTransitQty         採購在途：關聯這筆明細、已核准但還沒入庫的 PO 數量
     */
    record PurchaseSupply(
            BigDecimal plannedNotOrderedQty,
            BigDecimal inTransitQty
    ) {

        public static final PurchaseSupply NONE = new PurchaseSupply(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
