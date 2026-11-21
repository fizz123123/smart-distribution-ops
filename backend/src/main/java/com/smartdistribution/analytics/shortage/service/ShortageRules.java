package com.smartdistribution.analytics.shortage.service;

import com.smartdistribution.analytics.common.service.SupplyStatus;
import com.smartdistribution.analytics.shortage.repository.projection.OpenPurchaseLineProjection;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 缺貨分析的計算與判斷規則，只有 static 方法、不查資料庫，可以直接寫單元測試
 */
public final class ShortageRules {

    //平均日出貨量看最近幾天（含今天）
    public static final int USAGE_DAYS = 30;

    private static final BigDecimal USAGE_DAYS_DECIMAL = BigDecimal.valueOf(USAGE_DAYS);

    private ShortageRules() {
    }

    /**
     * @param shippedQty 近 30 天的出貨量
     * @return 平均日出貨量，小數 3 位
     */
    public static BigDecimal averageDailyShipment(BigDecimal shippedQty) {
        return shippedQty.divide(USAGE_DAYS_DECIMAL, 3, RoundingMode.HALF_UP);
    }

    /**
     * 可支撐天數 ＝ 可用量 ÷ 平均日出貨量 ＝ 可用量 × 30 ÷ 近 30 天出貨量；無條件捨去到小數 1 位，不會高估
     *
     * @return 最近沒有出貨時算不出來，回傳 null
     */
    public static BigDecimal coverageDays(BigDecimal availableQty, BigDecimal shippedQty) {
        if (shippedQty.signum() <= 0) {
            return null;
        }
        return availableQty.multiply(USAGE_DAYS_DECIMAL).divide(shippedQty, 1, RoundingMode.DOWN);
    }

    /**
     * 建議再訂購點 ＝ 平均日出貨量 × 平均採購週期，也就是從下單到貨這段時間預期會用掉的量
     * 用近 30 天出貨量直接算，不用四捨五入過的日出貨量；依基準單位的小數位無條件進位，寧可多備一點
     *
     * @return 沒有採購週期的資料時算不出來，回傳 null
     */
    public static BigDecimal reorderPoint(BigDecimal shippedQty, BigDecimal leadTimeDays, UnitOfMeasure baseUnit) {
        if (leadTimeDays == null) {
            return null;
        }
        return shippedQty.multiply(leadTimeDays)
                .divide(USAGE_DAYS_DECIMAL, baseUnit.getDecimalPlaces(), RoundingMode.CEILING);
    }

    /**
     * 停用的料號不會再補貨，所以不判斷庫存面的風險
     *
     * @param safetyStockQty 沒有設定時是 null
     * @param reorderPoint   算不出來時是 null
     * @return 庫存面的風險
     */
    public static List<ShortageRisk> stockRisks(
            boolean active,
            BigDecimal availableQty,
            BigDecimal safetyStockQty,
            BigDecimal inTransitQty,
            BigDecimal reorderPoint
    ) {
        List<ShortageRisk> risks = new ArrayList<>();
        if (!active) {
            return risks;
        }
        if (safetyStockQty != null && availableQty.compareTo(safetyStockQty) < 0) {
            risks.add(ShortageRisk.BELOW_SAFETY_STOCK);
        }
        if (reorderPoint != null && availableQty.add(inTransitQty).compareTo(reorderPoint) < 0) {
            risks.add(ShortageRisk.BELOW_REORDER_POINT);
        }
        return risks;
    }

    /**
     * 已經接下的 SO 不管料號有沒有停用都要出貨，所以供應面的風險一律判斷
     *
     * @param lines         這個料號的已核准 SO 明細
     * @param purchaseLines 這個料號還沒結的 PO 明細，包含一般補庫
     * @return 供應面的風險
     */
    public static List<ShortageRisk> supplyRisks(
            List<ApprovedLineSupply> lines,
            List<OpenPurchaseLineProjection> purchaseLines,
            LocalDate today
    ) {
        List<ShortageRisk> risks = new ArrayList<>();
        if (lines.stream().anyMatch(line -> line.unplannedQty().signum() > 0)) {
            risks.add(ShortageRisk.UNPLANNED_DEMAND);
        }
        if (lines.stream().anyMatch(line -> line.plannedNotOrderedQty().signum() > 0)) {
            risks.add(ShortageRisk.PO_NOT_APPROVED);
        }
        if (lines.stream().anyMatch(SupplyStatus::arrivesAfterDelivery)) {
            risks.add(ShortageRisk.ARRIVAL_AFTER_DELIVERY);
        }
        if (purchaseLines.stream().anyMatch(po -> po.approved()
                && po.expectedArrivalDate() != null
                && po.expectedArrivalDate().isBefore(today))) {
            risks.add(ShortageRisk.ARRIVAL_OVERDUE);
        }
        return risks;
    }
}
