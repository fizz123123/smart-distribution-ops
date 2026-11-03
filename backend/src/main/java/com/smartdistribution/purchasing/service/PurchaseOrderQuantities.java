package com.smartdistribution.purchasing.service;

import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.repository.SalesOrderLineQuantityProjection;
import com.smartdistribution.sales.service.SalesOrderDemandService.SalesOrderLineDemand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 關聯 SO 的 PO 數量，全部以庫存基準單位表示：
 * - 已規劃未下單 ＝ PO 狀態為 DRAFT / PENDING_APPROVAL / RETURNED 的明細數量
 * - 採購在途     ＝ PO 狀態為 APPROVED / PARTIALLY_RECEIVED 的明細未結數量
 * - 還能規劃     ＝ 剩餘需求 － 已預留 － 已規劃未下單 － 採購在途，最低為 0
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderQuantities {

    //不需要排除任何 PO 時傳入；PO 的 ID 從 1 開始
    public static final Long NO_EXCLUDED_PURCHASE_ORDER = 0L;

    private static final List<PurchaseOrderStatus> PLANNED_STATUSES = List.of(
            PurchaseOrderStatus.DRAFT,
            PurchaseOrderStatus.PENDING_APPROVAL,
            PurchaseOrderStatus.RETURNED
    );

    private static final List<PurchaseOrderStatus> IN_TRANSIT_STATUSES = List.of(
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private final PurchaseOrderLineRepository purchaseOrderLineRepository;

    public Map<Long, BigDecimal> findPlannedQty(Collection<Long> salesOrderLineIds, Long excludedPurchaseOrderId) {
        return sum(salesOrderLineIds, PLANNED_STATUSES, excludedPurchaseOrderId);
    }

    public Map<Long, BigDecimal> findInTransitQty(Collection<Long> salesOrderLineIds, Long excludedPurchaseOrderId) {
        return sum(salesOrderLineIds, IN_TRANSIT_STATUSES, excludedPurchaseOrderId);
    }

    /**
     * 每筆 SO 明細還能再規劃多少
     *
     * @param excludedPurchaseOrderId 修改草稿或核准時排除 PO 自己，否則會被自己原本的數量擋下
     */
    public Map<Long, BigDecimal> findPlannableQty(
            Map<Long, SalesOrderLineDemand> demands,
            Long excludedPurchaseOrderId
    ) {
        if (demands.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> planned = findPlannedQty(demands.keySet(), excludedPurchaseOrderId);
        Map<Long, BigDecimal> inTransit = findInTransitQty(demands.keySet(), excludedPurchaseOrderId);

        Map<Long, BigDecimal> plannable = new HashMap<>();
        demands.forEach((salesOrderLineId, demand) -> plannable.put(
                salesOrderLineId,
                demand.remainingQty()
                        .subtract(demand.reservedQty())
                        .subtract(planned.getOrDefault(salesOrderLineId, BigDecimal.ZERO))
                        .subtract(inTransit.getOrDefault(salesOrderLineId, BigDecimal.ZERO))
                        .max(BigDecimal.ZERO)
        ));

        return plannable;
    }

    private Map<Long, BigDecimal> sum(
            Collection<Long> salesOrderLineIds,
            List<PurchaseOrderStatus> statuses,
            Long excludedPurchaseOrderId
    ) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }

        return purchaseOrderLineRepository
                .sumQtyBySalesOrderLines(salesOrderLineIds, statuses, excludedPurchaseOrderId)
                .stream()
                .collect(Collectors.toMap(
                        SalesOrderLineQuantityProjection::salesOrderLineId,
                        SalesOrderLineQuantityProjection::openQty
                ));
    }
}
