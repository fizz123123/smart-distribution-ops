package com.smartdistribution.analytics.shortage.mapper;

import com.smartdistribution.analytics.common.service.AnalyticsNote;
import com.smartdistribution.analytics.common.service.SupplyStatus;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse.AffectedLine;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse.ItemRisk;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse.PurchaseOrderRef;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse.Replenishment;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse.Stock;
import com.smartdistribution.analytics.shortage.repository.projection.CustomerNameProjection;
import com.smartdistribution.analytics.shortage.repository.projection.ItemStockProjection;
import com.smartdistribution.analytics.shortage.repository.projection.OpenPurchaseLineProjection;
import com.smartdistribution.analytics.shortage.service.ShortageRules;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 把庫存、SO 明細的供應狀態與還沒結的 PO 依料號組起來，判斷風險，只留下有風險的料號
 */
@Component
public class ShortageAnalysisMapper {

    //受影響的 SO 明細：還沒著落、會來不及，或排進的 PO 還沒核准；準時在途與已備齊的不算
    private static final Set<SupplyStatus> AFFECTED_STATUSES = EnumSet.of(
            SupplyStatus.UNPLANNED,
            SupplyStatus.PURCHASE_DELAY_RISK,
            SupplyStatus.PLANNED_NOT_ORDERED
    );

    //SO 明細關聯的 PO 中還沒結的；已取消、已結案的只是歷史紀錄
    private static final Set<String> OPEN_PO_STATUSES = Set.of(
            "DRAFT", "PENDING_APPROVAL", "RETURNED", "APPROVED", "PARTIALLY_RECEIVED"
    );

    /**
     * @param today         今天（台北時間）
     * @param items         每個料號的庫存、出貨量與採購週期
     * @param supplies      已核准 SO 明細的供應狀態，依交期排序
     * @param purchaseLines 還沒結的 PO 明細，依預計到貨日排序
     * @param customers     客戶 ID → 代碼與名稱
     * @return 有風險的料號，依最早交期排序
     */
    public ShortageAnalysisResponse toResponse(
            LocalDate today,
            List<ItemStockProjection> items,
            List<ApprovedLineSupply> supplies,
            List<OpenPurchaseLineProjection> purchaseLines,
            Map<Long, CustomerNameProjection> customers
    ) {
        Map<Long, List<ApprovedLineSupply>> suppliesByItem = supplies.stream()
                .collect(Collectors.groupingBy(ApprovedLineSupply::companyItemId));
        Map<Long, List<OpenPurchaseLineProjection>> purchasesByItem = purchaseLines.stream()
                .collect(Collectors.groupingBy(OpenPurchaseLineProjection::companyItemId));

        List<ItemRisk> riskyItems = items.stream()
                .map(item -> toItemRisk(
                        item,
                        suppliesByItem.getOrDefault(item.companyItemId(), List.of()),
                        purchasesByItem.getOrDefault(item.companyItemId(), List.of()),
                        customers,
                        today
                ))
                .filter(item -> !item.stockRisks().isEmpty() || !item.supplyRisks().isEmpty())
                .sorted(Comparator
                        .comparing(ItemRisk::earliestDeliveryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ItemRisk::itemCode))
                .toList();

        return new ShortageAnalysisResponse(today, riskyItems);
    }

    private static ItemRisk toItemRisk(
            ItemStockProjection item,
            List<ApprovedLineSupply> lines,
            List<OpenPurchaseLineProjection> purchaseLines,
            Map<Long, CustomerNameProjection> customers,
            LocalDate today
    ) {
        BigDecimal plannedNotOrderedQty = sumOpenQty(purchaseLines, line -> !line.approved());
        BigDecimal inTransitQty = sumOpenQty(purchaseLines, OpenPurchaseLineProjection::approved);
        BigDecimal unplannedQty = lines.stream()
                .map(ApprovedLineSupply::unplannedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal coverageDays = ShortageRules.coverageDays(item.availableQty(), item.shippedQty());
        BigDecimal reorderPoint = ShortageRules.reorderPoint(
                item.shippedQty(), item.averageLeadTimeDays(), item.baseUnit()
        );

        List<AffectedLine> affectedLines = lines.stream()
                .map(line -> toAffectedLine(line, SupplyStatus.of(line, today), customers.get(line.customerId())))
                .filter(line -> AFFECTED_STATUSES.contains(line.supplyStatus()))
                .toList();

        return new ItemRisk(
                item.companyItemId(),
                item.itemCode(),
                item.itemName(),
                item.baseUnit(),
                item.active(),
                ShortageRules.stockRisks(
                        item.active(), item.availableQty(), item.safetyStockQty(), inTransitQty, reorderPoint
                ),
                ShortageRules.supplyRisks(lines, purchaseLines, today),
                affectedLines.stream()
                        .map(AffectedLine::deliveryDate)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null),
                new Stock(
                        item.onHandQty(),
                        item.reservedQty(),
                        item.availableQty(),
                        item.safetyStockQty(),
                        ShortageRules.averageDailyShipment(item.shippedQty()),
                        coverageDays,
                        coverageDays == null ? AnalyticsNote.NO_RECENT_USAGE : null,
                        item.averageLeadTimeDays(),
                        item.averageLeadTimeDays() == null ? AnalyticsNote.INSUFFICIENT_HISTORY : null,
                        reorderPoint
                ),
                new Replenishment(unplannedQty, plannedNotOrderedQty, inTransitQty),
                affectedLines,
                purchaseLines.stream()
                        .map(line -> new PurchaseOrderRef(
                                line.purchaseOrderId(),
                                line.poNumber(),
                                line.status(),
                                line.expectedArrivalDate(),
                                line.openQty()
                        ))
                        .toList()
        );
    }

    private static AffectedLine toAffectedLine(
            ApprovedLineSupply line,
            SupplyStatus supplyStatus,
            CustomerNameProjection customer
    ) {
        return new AffectedLine(
                line.salesOrderId(),
                line.soNumber(),
                line.salesOrderLineId(),
                line.lineNo(),
                line.customerId(),
                customer == null ? null : customer.customerCode(),
                customer == null ? null : customer.customerName(),
                line.deliveryDate(),
                line.remainingQty(),
                line.unplannedQty(),
                supplyStatus,
                line.purchaseOrders().stream()
                        .filter(po -> OPEN_PO_STATUSES.contains(po.status()) && "OPEN".equals(po.lineStatus()))
                        .map(ShortageAnalysisMapper::toPurchaseOrderRef)
                        .toList()
        );
    }

    private static PurchaseOrderRef toPurchaseOrderRef(LinkedPurchaseOrder po) {
        return new PurchaseOrderRef(
                po.purchaseOrderId(),
                po.poNumber(),
                po.status(),
                po.expectedArrivalDate(),
                po.baseQty().subtract(po.receivedQty())
        );
    }

    private static BigDecimal sumOpenQty(
            List<OpenPurchaseLineProjection> lines,
            Predicate<OpenPurchaseLineProjection> condition
    ) {
        return lines.stream()
                .filter(condition)
                .map(OpenPurchaseLineProjection::openQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
