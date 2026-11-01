package com.smartdistribution.fulfillment.service;

import com.smartdistribution.fulfillment.repository.DeliveryOrderLineRepository;
import com.smartdistribution.fulfillment.repository.ShipmentQuantityProjection;
import com.smartdistribution.sales.service.ShipmentQuantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * SO 明細的出貨數量，全部以庫存基準單位表示：
 * - 進行中 DO ＝ DRAFT 的預計量 ＋ READY 的實際量（揀貨時少出的差額不再占用）
 * - 已出貨     ＝ POSTED 的實際量
 * 作廢的 DO 不算
 */
@Component
@RequiredArgsConstructor
public class DeliveryOrderQuantities {

    private final DeliveryOrderLineRepository deliveryOrderLineRepository;

    public Map<Long, ShipmentQuantity> findBySalesOrderLines(Collection<Long> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> delivering = new HashMap<>();
        Map<Long, BigDecimal> shipped = new HashMap<>();

        for (ShipmentQuantityProjection row : deliveryOrderLineRepository.sumBySalesOrderLines(salesOrderLineIds)) {
            switch (row.status()) {
                case DRAFT -> delivering.merge(row.salesOrderLineId(), row.plannedQty(), BigDecimal::add);
                case READY -> delivering.merge(row.salesOrderLineId(), row.actualQty(), BigDecimal::add);
                case POSTED -> shipped.merge(row.salesOrderLineId(), row.actualQty(), BigDecimal::add);
                default -> {
                    //作廢的 DO 在查詢時已經排除
                }
            }
        }

        Map<Long, ShipmentQuantity> result = new HashMap<>();
        for (Long salesOrderLineId : salesOrderLineIds) {
            if (delivering.containsKey(salesOrderLineId) || shipped.containsKey(salesOrderLineId)) {
                result.put(salesOrderLineId, new ShipmentQuantity(
                        shipped.getOrDefault(salesOrderLineId, BigDecimal.ZERO),
                        delivering.getOrDefault(salesOrderLineId, BigDecimal.ZERO)
                ));
            }
        }

        return result;
    }
}
