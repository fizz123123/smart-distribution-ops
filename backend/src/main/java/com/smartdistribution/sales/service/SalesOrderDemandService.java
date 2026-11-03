package com.smartdistribution.sales.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * 其他模組查詢 SO 明細需求的入口，不直接存取 SO 的資料表
 */
public interface SalesOrderDemandService {

    /**
     * @return SO 明細 ID → 需求；不存在的明細不會出現在結果中
     */
    Map<Long, SalesOrderLineDemand> findDemands(Collection<Long> salesOrderLineIds);

    /**
     * 給採購模組的 SO 明細需求，採購用它檢查與計算「還能規劃多少」
     *
     * @param approved     SO 是否為已核准；只有已核准的 SO 能關聯 PO
     * @param remainingQty 剩餘需求 ＝ 有效訂購量 － 已出貨量（基準單位）；SO 不是已核准時為 0
     * @param reservedQty  目前有效預留（基準單位）
     */
    record SalesOrderLineDemand(
            Long salesOrderLineId,
            Long salesOrderId,
            String salesOrderNumber,
            Integer lineNo,
            Long companyItemId,
            boolean approved,
            BigDecimal remainingQty,
            BigDecimal reservedQty
    ) {
    }
}
