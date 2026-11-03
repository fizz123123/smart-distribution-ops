package com.smartdistribution.sales.service;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.entity.DeliveryMethod;
import java.util.List;
import java.math.BigDecimal;
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

    /**
     * DO 過帳後呼叫：每筆明細都已出貨或取消（剩餘需求都是 0）時，SO 履約結案
     */
    void closeIfFulfilled(Long salesOrderId);

    /**
     * 出貨模組需要的 SO 資料：交貨資料印在出貨單上，明細的單位與換算倍率是送審時的快照
     *
     * @param approved 只有已核准的 SO 能通知出貨
     */
    record SalesOrderForDelivery(
            Long id,
            String soNumber,
            boolean approved,
            Long customerId,
            String customerOrderNumber,
            DeliveryMethod deliveryMethod,
            String deliveryAddress,
            String contactName,
            String contactPhone,
            List<Line> lines
    ) {

        public record Line(
                Long id,
                Integer lineNo,
                Long companyItemId,
                String customerItemCode,
                UnitOfMeasure unit,
                BigDecimal conversionFactor
        ) {
        }
    }
}
