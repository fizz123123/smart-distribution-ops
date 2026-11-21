package com.smartdistribution.analytics.common.service;

import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;

import java.time.LocalDate;
import java.util.Set;

/**
 * 已核准 SO 明細的供應狀態：每一筆只歸一個狀態，由上往下先符合的為準，所以各狀態的金額加起來等於總額
 * 已接單未出貨的拆解和缺貨風險的受影響明細共用；只標示狀態，不拿來解釋營收變化
 */
public enum SupplyStatus {
    UNPLANNED,           //缺料待處理：還有尚待規劃的數量
    PURCHASE_DELAY_RISK, //採購延遲風險：關聯的已核准 PO 預計晚於交期到貨，或已經過了預計到貨日
    PLANNED_NOT_ORDERED, //已規劃、未下單：缺的量排進了還沒核准的 PO
    PARTIALLY_RECEIVED,  //部分到貨：關聯的 PO 已經收過貨，還有在途
    IN_TRANSIT,          //採購在途：等已核准的 PO 到貨
    READY;               //已備齊：剩餘需求都已預留

    //PO 狀態用字串，和 LinkedPurchaseOrder 一樣不依賴採購模組的列舉
    private static final Set<String> OPEN_PO_STATUSES = Set.of("APPROVED", "PARTIALLY_RECEIVED");

    /**
     * 缺料影響：還沒有著落，或有著落但會來不及；用在總覽的缺料影響金額
     */
    public boolean affectsShipment() {
        return this == UNPLANNED || this == PURCHASE_DELAY_RISK;
    }

    /**
     * @param supply 已核准 SO 明細的供應狀態
     * @param today  今天（台北時間）
     * @return 這一行的供應狀態
     */
    public static SupplyStatus of(ApprovedLineSupply supply, LocalDate today) {
        if (supply.unplannedQty().signum() > 0) {
            return UNPLANNED;
        }
        if (supply.purchaseOrders().stream().anyMatch(po -> isOpen(po) && isLate(po, supply.deliveryDate(), today))) {
            return PURCHASE_DELAY_RISK;
        }
        if (supply.plannedNotOrderedQty().signum() > 0) {
            return PLANNED_NOT_ORDERED;
        }
        if (supply.purchaseOrders().stream().anyMatch(po -> isOpen(po) && po.receivedQty().signum() > 0)) {
            return PARTIALLY_RECEIVED;
        }
        if (supply.inTransitQty().signum() > 0) {
            return IN_TRANSIT;
        }
        return READY;
    }

    //已核准、這一行還在等貨的 PO
    private static boolean isOpen(LinkedPurchaseOrder po) {
        return OPEN_PO_STATUSES.contains(po.status()) && "OPEN".equals(po.lineStatus());
    }

    //預計到貨日晚於交期，或今天已經過了預計到貨日還沒收齊
    private static boolean isLate(LinkedPurchaseOrder po, LocalDate deliveryDate, LocalDate today) {
        LocalDate arrival = po.expectedArrivalDate();
        if (arrival == null) {
            return false;
        }
        return (deliveryDate != null && arrival.isAfter(deliveryDate)) || arrival.isBefore(today);
    }
}
