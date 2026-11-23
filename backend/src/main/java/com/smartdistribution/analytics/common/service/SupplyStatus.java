package com.smartdistribution.analytics.common.service;

import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;

import java.time.LocalDate;

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
        if (arrivesAfterDelivery(supply) || hasOverdueArrival(supply, today)) {
            return PURCHASE_DELAY_RISK;
        }
        if (supply.plannedNotOrderedQty().signum() > 0) {
            return PLANNED_NOT_ORDERED;
        }
        if (supply.purchaseOrders().stream().anyMatch(po -> isWaiting(po) && po.receivedQty().signum() > 0)) {
            return PARTIALLY_RECEIVED;
        }
        if (supply.inTransitQty().signum() > 0) {
            return IN_TRANSIT;
        }
        return READY;
    }

    /**
     * 缺貨分析的「到貨晚於交期」也用這個判斷；已核准的 PO 送審時一定有預計到貨日
     *
     * @return 關聯的已核准 PO 還在等貨，預計到貨日卻晚於這一行的交期
     */
    public static boolean arrivesAfterDelivery(ApprovedLineSupply supply) {
        //交期沒有值的是明細交期功能上線前的舊資料，不判斷
        LocalDate deliveryDate = supply.deliveryDate();
        return deliveryDate != null && supply.purchaseOrders().stream()
                .anyMatch(po -> isWaiting(po) && po.expectedArrivalDate().isAfter(deliveryDate));
    }

    //關聯的已核准 PO 已經過了預計到貨日，還沒收齊
    private static boolean hasOverdueArrival(ApprovedLineSupply supply, LocalDate today) {
        return supply.purchaseOrders().stream()
                .anyMatch(po -> isWaiting(po) && po.expectedArrivalDate().isBefore(today));
    }

    private static boolean isWaiting(LinkedPurchaseOrder po) {
        return PurchaseStatusRules.isApprovedAndOpen(po.status(), po.lineStatus());
    }
}
