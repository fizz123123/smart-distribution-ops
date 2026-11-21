package com.smartdistribution.analytics.shortage.dto.response;

import com.smartdistribution.analytics.common.service.AnalyticsNote;
import com.smartdistribution.analytics.common.service.SupplyStatus;
import com.smartdistribution.analytics.shortage.service.ShortageRisk;
import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 缺貨與採購：以 asOf（今天）為準，只列出至少有一項風險的料號
 * 數量都是庫存基準單位；不含任何價格，採購、倉管也看得到這份分析
 *
 * @param items 依受影響 SO 明細的最早交期排序，最急的在前；只有庫存面風險的料號排在後面
 */
public record ShortageAnalysisResponse(
        LocalDate asOf,
        List<ItemRisk> items
) {

    /**
     * @param stockRisks           庫存面的風險：料號整體還夠不夠
     * @param supplyRisks          供應面的風險：已接下的 SO 能不能如期備齊
     * @param earliestDeliveryDate 受影響 SO 明細中最早的交期；沒有受影響的明細時是 null
     * @param affectedLines        供應狀態是缺料待處理、採購延遲風險、已規劃未下單的 SO 明細，依交期排序
     * @param purchaseOrders       這個料號還沒結的 PO，包含一般補庫的 PO，依預計到貨日排序
     */
    public record ItemRisk(
            Long companyItemId,
            String itemCode,
            String itemName,
            UnitOfMeasure baseUnit,
            boolean active,
            List<ShortageRisk> stockRisks,
            List<ShortageRisk> supplyRisks,
            LocalDate earliestDeliveryDate,
            Stock stock,
            Replenishment replenishment,
            List<AffectedLine> affectedLines,
            List<PurchaseOrderRef> purchaseOrders
    ) {
    }

    /**
     * @param availableQty         實體數量 － 已預留
     * @param safetyStockQty       採購主管設定的安全庫存
     * @param averageDailyShipment 近 30 天（含今天）已過帳 DO 的出貨量 ÷ 30
     * @param coverageDays         可用量 ÷ 平均日出貨量；最近沒有出貨時是 null，附 NO_RECENT_USAGE
     * @param averageLeadTimeDays  最近六個月收齊的 PO 明細，從核准到最後一次合格收貨的平均天數；
     *                             沒有資料時是 null，附 INSUFFICIENT_HISTORY
     * @param reorderPoint         建議再訂購點 ＝ 平均日出貨量 × 平均採購週期；沒有採購週期時是 null
     */
    public record Stock(
            BigDecimal onHandQty,
            BigDecimal reservedQty,
            BigDecimal availableQty,
            BigDecimal safetyStockQty,
            BigDecimal averageDailyShipment,
            BigDecimal coverageDays,
            AnalyticsNote coverageNote,
            BigDecimal averageLeadTimeDays,
            AnalyticsNote leadTimeNote,
            BigDecimal reorderPoint
    ) {
    }

    /**
     * 料號層級的補貨進度；已規劃未下單、採購在途包含一般補庫的 PO
     *
     * @param unplannedQty         所有已核准 SO 明細的尚待規劃加總
     * @param plannedNotOrderedQty 還沒核准的 PO 數量
     * @param inTransitQty         已核准、還沒到的 PO 數量
     */
    public record Replenishment(
            BigDecimal unplannedQty,
            BigDecimal plannedNotOrderedQty,
            BigDecimal inTransitQty
    ) {
    }

    /**
     * @param remainingQty   剩餘需求 ＝ 訂購 － 已核准取消 － 已出貨
     * @param unplannedQty   尚待規劃
     * @param purchaseOrders 關聯這一行、還沒結的 PO
     */
    public record AffectedLine(
            Long salesOrderId,
            String soNumber,
            Long salesOrderLineId,
            Integer lineNo,
            Long customerId,
            String customerCode,
            String customerName,
            LocalDate deliveryDate,
            BigDecimal remainingQty,
            BigDecimal unplannedQty,
            SupplyStatus supplyStatus,
            List<PurchaseOrderRef> purchaseOrders
    ) {
    }

    /**
     * @param status  PO 狀態，例如 DRAFT、APPROVED
     * @param openQty 還沒到的數量
     */
    public record PurchaseOrderRef(
            Long purchaseOrderId,
            String poNumber,
            String status,
            LocalDate expectedArrivalDate,
            BigDecimal openQty
    ) {
    }
}
