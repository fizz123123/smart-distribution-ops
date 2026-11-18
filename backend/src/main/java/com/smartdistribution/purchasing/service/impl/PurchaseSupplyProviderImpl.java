package com.smartdistribution.purchasing.service.impl;

import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.service.PurchaseOrderQuantities;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.PurchaseSupply;
import com.smartdistribution.sales.service.PurchaseSupplyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 銷售模組定義、採購模組實作：提供 SO 詳情顯示的已規劃未下單、採購在途與關聯的 PO
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseSupplyProviderImpl implements PurchaseSupplyProvider {

    private final PurchaseOrderQuantities purchaseOrderQuantities;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;

    @Override
    public Map<Long, PurchaseSupply> findBySalesOrderLines(Collection<Long> salesOrderLineIds) {
        Map<Long, BigDecimal> planned = purchaseOrderQuantities.findPlannedQty(
                salesOrderLineIds, PurchaseOrderQuantities.NO_EXCLUDED_PURCHASE_ORDER
        );
        Map<Long, BigDecimal> inTransit = purchaseOrderQuantities.findInTransitQty(
                salesOrderLineIds, PurchaseOrderQuantities.NO_EXCLUDED_PURCHASE_ORDER
        );

        //所有狀態的 PO 都列出來；只有已取消或已結案 PO 的明細，數量是 0 但仍要顯示紀錄
        Map<Long, List<LinkedPurchaseOrder>> linkedByLine = new HashMap<>();
        for (PurchaseOrderLine line : purchaseOrderLineRepository
                .findAllBySalesOrderLineIdInOrderByPurchaseOrderIdAscLineNoAsc(salesOrderLineIds)) {
            linkedByLine.computeIfAbsent(line.getSalesOrderLineId(), id -> new ArrayList<>())
                    .add(toLinkedPurchaseOrder(line));
        }

        Map<Long, PurchaseSupply> supplies = new HashMap<>();
        linkedByLine.forEach((salesOrderLineId, purchaseOrders) -> supplies.put(salesOrderLineId, new PurchaseSupply(
                planned.getOrDefault(salesOrderLineId, BigDecimal.ZERO),
                inTransit.getOrDefault(salesOrderLineId, BigDecimal.ZERO),
                purchaseOrders
        )));

        return supplies;
    }

    private LinkedPurchaseOrder toLinkedPurchaseOrder(PurchaseOrderLine line) {
        PurchaseOrder purchaseOrder = line.getPurchaseOrder();

        return new LinkedPurchaseOrder(
                purchaseOrder.getId(),
                purchaseOrder.getPoNumber(),
                purchaseOrder.getStatus().name(),
                line.getStatus().name(),
                line.getBaseQty(),
                line.getAcceptedBaseQty(),
                purchaseOrder.getExpectedArrivalDate()
        );
    }
}
