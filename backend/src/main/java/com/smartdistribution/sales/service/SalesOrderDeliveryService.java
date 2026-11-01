package com.smartdistribution.sales.service;

import java.util.Collection;
import java.util.Map;

/**
 * 出貨模組讀取 SO 的入口，不直接存取 SO 的資料表
 */
public interface SalesOrderDeliveryService {

    /**
     * @return SO ID → 出貨需要的 SO 資料；不存在的 SO 不會出現在結果中
     */
    Map<Long, SalesOrderForDelivery> findForDelivery(Collection<Long> salesOrderIds);
}
