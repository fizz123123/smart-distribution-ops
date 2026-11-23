package com.smartdistribution.sales.service;

import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.repository.CancelledQuantityProjection;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.PurchaseSupply;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已核准 SO 明細的供應數量：預留、已規劃未下單、採購在途與尚待規劃
 * SO 詳情、缺料清單與儀表板共用，尚待規劃只有這一個算法
 * 已規劃未下單與採購在途由採購模組提供，已出貨與進行中 DO 由出貨模組提供
 */
@Component
@RequiredArgsConstructor
public class SalesOrderSupplyQuantities {

    private final SalesOrderQuantities salesOrderQuantities;
    private final InventoryService inventoryService;
    private final PurchaseSupplyProvider purchaseSupplyProvider;

    /**
     * @param lines 已核准或已結案 SO 的明細
     * @return SO 明細 ID → 供應數量
     */
    public Map<Long, Supply> findSupplies(List<SalesOrderLine> lines) {
        return findSupplies(
                lines,
                salesOrderQuantities.findCancellations(lines.stream().map(SalesOrderLine::getId).toList())
        );
    }

    /**
     * SO 詳情已經查過取消數量，直接傳進來，不必再查一次
     *
     * @param lines         已核准或已結案 SO 的明細
     * @param cancellations SO 明細 ID → 已核准取消的數量
     * @return SO 明細 ID → 供應數量
     */
    public Map<Long, Supply> findSupplies(
            List<SalesOrderLine> lines,
            Map<Long, CancelledQuantityProjection> cancellations
    ) {
        List<Long> lineIds = lines.stream().map(SalesOrderLine::getId).toList();
        if (lineIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> reservedByLine = inventoryService.findOpenQtyBySalesOrderLines(lineIds);
        Map<Long, BigDecimal> cancelledByLine = new HashMap<>();
        cancellations.forEach((lineId, cancellation) -> cancelledByLine.put(lineId, cancellation.cancelledQty()));
        Map<Long, PurchaseSupply> purchaseByLine = purchaseSupplyProvider.findBySalesOrderLines(lineIds);
        Map<Long, ShipmentQuantity> shipmentsByLine = salesOrderQuantities.findShipments(lineIds);

        Map<Long, Supply> supplies = new HashMap<>();
        for (SalesOrderLine line : lines) {
            supplies.put(
                    line.getId(),
                    toSupply(line, reservedByLine, cancelledByLine, purchaseByLine, shipmentsByLine)
            );
        }

        return supplies;
    }

    /**
     * 尚待規劃 ＝ 剩餘需求 － 已預留 － 已規劃未下單 － 採購在途，最低為 0
     * 剩餘需求用 SalesOrderQuantities.remainingQty，和取消、結案的判斷同一個算法
     * 進行中 DO 的數量仍在預留裡，所以不另外扣
     */
    private Supply toSupply(
            SalesOrderLine line,
            Map<Long, BigDecimal> reservedByLine,
            Map<Long, BigDecimal> cancelledByLine,
            Map<Long, PurchaseSupply> purchaseByLine,
            Map<Long, ShipmentQuantity> shipmentsByLine
    ) {
        BigDecimal reservedQty = reservedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
        PurchaseSupply purchase = purchaseByLine.getOrDefault(line.getId(), PurchaseSupply.NONE);
        ShipmentQuantity shipment = shipmentsByLine.getOrDefault(line.getId(), ShipmentQuantity.NONE);

        BigDecimal unplannedQty = salesOrderQuantities.remainingQty(line, cancelledByLine, shipmentsByLine)
                .subtract(reservedQty)
                .subtract(purchase.plannedNotOrderedQty())
                .subtract(purchase.inTransitQty())
                .max(BigDecimal.ZERO);

        return new Supply(
                reservedQty,
                purchase.plannedNotOrderedQty(),
                purchase.inTransitQty(),
                shipment.deliveringQty(),
                shipment.shippedQty(),
                cancelledByLine.getOrDefault(line.getId(), BigDecimal.ZERO),
                unplannedQty,
                purchase.purchaseOrders()
        );
    }

    /**
     * 一筆 SO 明細的供應數量，全部以庫存基準單位表示；SO 詳情直接把它放進回應
     *
     * @param deliveringQty  已排入 DO、還沒過帳的數量，仍在預留裡；可再通知出貨 ＝ 預留 － 這個數量
     * @param purchaseOrders 關聯這一行的所有 PO，不含單價
     */
    public record Supply(
            BigDecimal reservedQty,
            BigDecimal plannedNotOrderedQty,
            BigDecimal inTransitQty,
            BigDecimal deliveringQty,
            BigDecimal shippedQty,
            BigDecimal cancelledQty,
            BigDecimal unplannedQty,
            List<LinkedPurchaseOrder> purchaseOrders
    ) {
    }
}
