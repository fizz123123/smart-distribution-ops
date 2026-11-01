package com.smartdistribution.purchasing.service.impl;

import com.smartdistribution.purchasing.service.PurchaseOrderQuantities;
import com.smartdistribution.sales.service.PurchaseSupply;
import com.smartdistribution.sales.service.PurchaseSupplyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 銷售模組定義、採購模組實作：提供 SO 詳情顯示的已規劃未下單與採購在途
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseSupplyProviderImpl implements PurchaseSupplyProvider {

    private final PurchaseOrderQuantities purchaseOrderQuantities;

    @Override
    public Map<Long, PurchaseSupply> findBySalesOrderLines(Collection<Long> salesOrderLineIds) {
        Map<Long, BigDecimal> planned = purchaseOrderQuantities.findPlannedQty(
                salesOrderLineIds, PurchaseOrderQuantities.NO_EXCLUDED_PURCHASE_ORDER
        );
        Map<Long, BigDecimal> inTransit = purchaseOrderQuantities.findInTransitQty(
                salesOrderLineIds, PurchaseOrderQuantities.NO_EXCLUDED_PURCHASE_ORDER
        );

        Set<Long> linkedLineIds = new HashSet<>(planned.keySet());
        linkedLineIds.addAll(inTransit.keySet());

        Map<Long, PurchaseSupply> supplies = new HashMap<>();
        for (Long salesOrderLineId : linkedLineIds) {
            supplies.put(salesOrderLineId, new PurchaseSupply(
                    planned.getOrDefault(salesOrderLineId, BigDecimal.ZERO),
                    inTransit.getOrDefault(salesOrderLineId, BigDecimal.ZERO)
            ));
        }

        return supplies;
    }
}
