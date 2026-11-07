package com.smartdistribution.fulfillment.service.impl;

import com.smartdistribution.fulfillment.repository.DeliveryOrderLineRepository;
import com.smartdistribution.fulfillment.repository.ShipmentQuantityProjection;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import com.smartdistribution.sales.service.ShipmentQuantityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 銷售模組定義、出貨模組實作：SO 明細的已出貨量與進行中 DO 數量，全部以庫存基準單位表示
 * - 進行中 DO ＝ DRAFT 的預計量 ＋ READY 的實際量（揀貨時少出的差額不再占用）
 * - 已出貨     ＝ POSTED 的實際量
 * 作廢的 DO 不算；出貨模組自己檢查可通知量時也用這裡的結果
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentQuantityProviderImpl implements ShipmentQuantityProvider {

    private final DeliveryOrderLineRepository deliveryOrderLineRepository;

    @Override
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
                case VOIDED -> {
                    //查詢時已經排除，不會進到這裡
                }
            }
        }

        Set<Long> linkedLineIds = new HashSet<>(delivering.keySet());
        linkedLineIds.addAll(shipped.keySet());

        Map<Long, ShipmentQuantity> shipments = new HashMap<>();
        for (Long salesOrderLineId : linkedLineIds) {
            shipments.put(salesOrderLineId, new ShipmentQuantity(
                    shipped.getOrDefault(salesOrderLineId, BigDecimal.ZERO),
                    delivering.getOrDefault(salesOrderLineId, BigDecimal.ZERO)
            ));
        }

        return shipments;
    }
}
