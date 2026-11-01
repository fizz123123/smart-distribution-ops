package com.smartdistribution.sales.service;

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
}
