package com.smartdistribution.analytics.common;

import com.smartdistribution.analytics.common.service.SupplyStatus;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 供應狀態的判斷順序：缺料 → 採購延遲 → 未下單 → 部分到貨 → 在途 → 已備齊，先符合的為準
 */
class SupplyStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    private static final LocalDate DELIVERY_DATE = TODAY.plusDays(10);

    @Test
    void unplannedComesFirstEvenWithLatePurchaseOrder() {
        ApprovedLineSupply supply = supply("2", "0", "3", openPo("0", DELIVERY_DATE.plusDays(5)));

        assertThat(SupplyStatus.of(supply, TODAY)).isEqualTo(SupplyStatus.UNPLANNED);
    }

    @Test
    void openPurchaseOrderArrivingAfterDeliveryOrAlreadyOverdueIsDelayRisk() {
        //預計到貨晚於交期
        assertThat(SupplyStatus.of(supply("0", "0", "5", openPo("0", DELIVERY_DATE.plusDays(1))), TODAY))
                .isEqualTo(SupplyStatus.PURCHASE_DELAY_RISK);
        //預計到貨日已經過了，還沒收齊
        assertThat(SupplyStatus.of(supply("0", "0", "5", openPo("2", TODAY.minusDays(1))), TODAY))
                .isEqualTo(SupplyStatus.PURCHASE_DELAY_RISK);
        //交期當天到貨不算晚
        assertThat(SupplyStatus.of(supply("0", "0", "5", openPo("0", DELIVERY_DATE)), TODAY))
                .isEqualTo(SupplyStatus.IN_TRANSIT);
    }

    @Test
    void closedOrCancelledPurchaseOrderIsNotDelayRisk() {
        LinkedPurchaseOrder cancelled = po("CANCELLED", "OPEN", "0", DELIVERY_DATE.plusDays(5));
        LinkedPurchaseOrder closedShort = po("CLOSED", "CLOSED_SHORT", "3", TODAY.minusDays(3));

        assertThat(SupplyStatus.of(supply("0", "4", "0", cancelled, closedShort), TODAY))
                .isEqualTo(SupplyStatus.PLANNED_NOT_ORDERED);
    }

    @Test
    void receivedPartOfOpenPurchaseOrderIsPartiallyReceived() {
        assertThat(SupplyStatus.of(supply("0", "0", "3", openPo("2", DELIVERY_DATE.minusDays(1))), TODAY))
                .isEqualTo(SupplyStatus.PARTIALLY_RECEIVED);
    }

    @Test
    void allReservedIsReady() {
        assertThat(SupplyStatus.of(supply("0", "0", "0"), TODAY)).isEqualTo(SupplyStatus.READY);
    }

    @Test
    void onlyUnplannedAndDelayRiskAffectShipment() {
        assertThat(SupplyStatus.UNPLANNED.affectsShipment()).isTrue();
        assertThat(SupplyStatus.PURCHASE_DELAY_RISK.affectsShipment()).isTrue();
        assertThat(SupplyStatus.PLANNED_NOT_ORDERED.affectsShipment()).isFalse();
        assertThat(SupplyStatus.READY.affectsShipment()).isFalse();
    }

    private static ApprovedLineSupply supply(
            String unplannedQty,
            String plannedNotOrderedQty,
            String inTransitQty,
            LinkedPurchaseOrder... purchaseOrders
    ) {
        return new ApprovedLineSupply(
                1L,
                "SO-000001",
                1L,
                1,
                1L,
                1L,
                1L,
                DELIVERY_DATE,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal(plannedNotOrderedQty),
                new BigDecimal(inTransitQty),
                new BigDecimal(unplannedQty),
                List.of(purchaseOrders)
        );
    }

    //已核准、這一行還在等貨的 PO
    private static LinkedPurchaseOrder openPo(String receivedQty, LocalDate expectedArrivalDate) {
        return po("APPROVED", "OPEN", receivedQty, expectedArrivalDate);
    }

    private static LinkedPurchaseOrder po(
            String status,
            String lineStatus,
            String receivedQty,
            LocalDate expectedArrivalDate
    ) {
        return new LinkedPurchaseOrder(
                1L,
                "PO-000001",
                status,
                lineStatus,
                new BigDecimal("5"),
                new BigDecimal(receivedQty),
                expectedArrivalDate
        );
    }
}
