package com.smartdistribution.sales.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * SO 需要的出貨數量，由出貨模組實作
 *
 * <p>出貨模組建 DO 時要讀 SO，SO 的已出貨量與可取消量又要看 DO；
 * 和採購一樣由銷售模組定義它需要什麼，出貨模組去實作，模組依賴只剩出貨 → 銷售一個方向
 */
public interface ShipmentQuantityProvider {

    /**
     * @return SO 明細 ID → 出貨數量；沒有任何 DO 的明細不會出現在結果中
     */
    Map<Long, ShipmentQuantity> findBySalesOrderLines(Collection<Long> salesOrderLineIds);

    /**
     * 一筆 SO 明細的出貨數量，以庫存基準單位表示
     *
     * @param shippedQty    已出貨：已過帳 DO 的實際出貨量
     * @param deliveringQty 進行中 DO：草稿算預計量、完成揀貨算實際量；這部分已排入出貨，不能取消
     */
    record ShipmentQuantity(
            BigDecimal shippedQty,
            BigDecimal deliveringQty
    ) {

        public static final ShipmentQuantity NONE = new ShipmentQuantity(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
