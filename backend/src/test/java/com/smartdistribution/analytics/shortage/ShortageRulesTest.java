package com.smartdistribution.analytics.shortage;

import com.smartdistribution.analytics.shortage.repository.projection.OpenPurchaseLineProjection;
import com.smartdistribution.analytics.shortage.service.ShortageRisk;
import com.smartdistribution.analytics.shortage.service.ShortageRules;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日出貨量、可支撐天數、再訂購點、比例的算法，以及六種風險的判斷
 */
class ShortageRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    private static final LocalDate DELIVERY_DATE = TODAY.plusDays(10);

    @Test
    void coverageDaysIsNullWithoutRecentShipments() {
        assertThat(ShortageRules.averageDailyShipment(new BigDecimal("45"))).isEqualByComparingTo("1.5");
        //10 × 30 ÷ 45 ＝ 6.67，無條件捨去成 6.6
        assertThat(ShortageRules.coverageDays(new BigDecimal("10"), new BigDecimal("45"))).isEqualByComparingTo("6.6");
        assertThat(ShortageRules.coverageDays(new BigDecimal("10"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void reorderPointRoundsUpByBaseUnitDecimalPlaces() {
        //45 × 7.5 ÷ 30 ＝ 11.25：個沒有小數，進位成 12；公斤有 3 位小數，維持 11.250
        assertThat(ShortageRules.reorderPoint(new BigDecimal("45"), new BigDecimal("7.5"), UnitOfMeasure.PCS))
                .isEqualByComparingTo("12");
        assertThat(ShortageRules.reorderPoint(new BigDecimal("45"), new BigDecimal("7.5"), UnitOfMeasure.KG))
                .isEqualByComparingTo("11.25");
        //沒有採購週期就算不出來；最近沒有出貨時是 0
        assertThat(ShortageRules.reorderPoint(new BigDecimal("45"), null, UnitOfMeasure.PCS)).isNull();
        assertThat(ShortageRules.reorderPoint(BigDecimal.ZERO, new BigDecimal("7.5"), UnitOfMeasure.PCS))
                .isEqualByComparingTo("0");
    }

    @Test
    void ratioIsNullWithoutData() {
        assertThat(ShortageRules.ratio(1, 3)).isEqualByComparingTo("0.3333");
        assertThat(ShortageRules.ratio(0, 4)).isEqualByComparingTo("0");
        assertThat(ShortageRules.ratio(0, 0)).isNull();
    }

    @Test
    void stockRisksCompareAvailableWithSafetyStockAndReorderPoint() {
        //可用 5 低於安全庫存 6；可用 5 ＋ 在途 6 ＝ 11 低於再訂購點 12
        assertThat(ShortageRules.stockRisks(true, qty("5"), qty("6"), qty("6"), qty("12")))
                .containsExactly(ShortageRisk.BELOW_SAFETY_STOCK, ShortageRisk.BELOW_REORDER_POINT);
        //在途 7 時剛好等於再訂購點，不算低於
        assertThat(ShortageRules.stockRisks(true, qty("5"), qty("5"), qty("7"), qty("12"))).isEmpty();
        //停用的料號不判斷庫存面
        assertThat(ShortageRules.stockRisks(false, qty("5"), qty("6"), qty("0"), qty("12"))).isEmpty();
        //沒有再訂購點時只看安全庫存
        assertThat(ShortageRules.stockRisks(true, qty("5"), qty("6"), qty("0"), null))
                .containsExactly(ShortageRisk.BELOW_SAFETY_STOCK);
    }

    @Test
    void supplyRisksComeFromSalesOrderLinesAndOpenPurchaseOrders() {
        ApprovedLineSupply unplanned = line("3", "0", List.of());
        ApprovedLineSupply plannedNotOrdered = line("0", "3", List.of());
        ApprovedLineSupply lateArrival = line("0", "0", List.of(linkedPo(DELIVERY_DATE.plusDays(1))));

        assertThat(ShortageRules.supplyRisks(List.of(unplanned, plannedNotOrdered, lateArrival), List.of(), TODAY))
                .containsExactly(
                        ShortageRisk.UNPLANNED_DEMAND,
                        ShortageRisk.PO_NOT_APPROVED,
                        ShortageRisk.ARRIVAL_AFTER_DELIVERY
                );
    }

    @Test
    void arrivalOverdueOnlyCountsApprovedPurchaseOrders() {
        //已核准、過了預計到貨日：一般補庫的 PO 也算
        assertThat(ShortageRules.supplyRisks(List.of(), List.of(purchaseLine("APPROVED", TODAY.minusDays(1))), TODAY))
                .containsExactly(ShortageRisk.ARRIVAL_OVERDUE);
        //草稿還沒下給供應商，不算逾期；今天到貨也還不算
        assertThat(ShortageRules.supplyRisks(List.of(), List.of(purchaseLine("DRAFT", TODAY.minusDays(1))), TODAY))
                .isEmpty();
        assertThat(ShortageRules.supplyRisks(List.of(), List.of(purchaseLine("APPROVED", TODAY)), TODAY))
                .isEmpty();
    }

    private static BigDecimal qty(String value) {
        return new BigDecimal(value);
    }

    private static ApprovedLineSupply line(
            String unplannedQty,
            String plannedNotOrderedQty,
            List<LinkedPurchaseOrder> purchaseOrders
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
                qty("5"),
                BigDecimal.ZERO,
                qty(plannedNotOrderedQty),
                BigDecimal.ZERO,
                qty(unplannedQty),
                purchaseOrders
        );
    }

    //已核准、這一行還在等貨的 PO
    private static LinkedPurchaseOrder linkedPo(LocalDate expectedArrivalDate) {
        return new LinkedPurchaseOrder(1L, "PO-000001", "APPROVED", "OPEN", qty("5"), qty("0"), expectedArrivalDate);
    }

    private static OpenPurchaseLineProjection purchaseLine(String status, LocalDate expectedArrivalDate) {
        return new OpenPurchaseLineProjection(1L, 1L, "PO-000001", status, expectedArrivalDate, qty("5"));
    }
}
