package com.smartdistribution.sales.service;

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
}
