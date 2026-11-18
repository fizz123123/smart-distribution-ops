package com.smartdistribution.sales.mapper;

import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.pricing.service.PriceLookupService;
import com.smartdistribution.pricing.service.PriceLookupService.ApplicablePrice;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse.Line;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse.Supply;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderShortageResponse;
import com.smartdistribution.sales.dto.response.SalesOrderSummaryResponse;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.service.PurchaseSupplyProvider.PurchaseSupply;
import com.smartdistribution.sales.service.PurchaseSupplyProvider;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderMasterData;
import com.smartdistribution.sales.service.SalesOrderQuantities;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 組出 SO 的回應，需要查主檔、使用者與庫存，所以不是單純的欄位轉換
 * 草稿與退回時依目前主檔試算並列出警示；送審後直接使用快照
 */
@Component
@RequiredArgsConstructor
public class SalesOrderAssembler {

    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderMasterData salesOrderMasterData;
    private final PriceLookupService priceLookupService;
    private final UserDirectoryService userDirectoryService;
    private final InventoryService inventoryService;
    private final SalesOrderQuantities salesOrderQuantities;
    private final PurchaseSupplyProvider purchaseSupplyProvider;

    public Page<SalesOrderSummaryResponse> toSummaries(Page<SalesOrder> salesOrders) {
        Set<Long> customerIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        salesOrders.forEach(salesOrder -> {
            customerIds.add(salesOrder.getCustomerId());
            userIds.add(salesOrder.getResponsibleSalesUserId());
        });

        Map<Long, Customer> customers = salesOrderMasterData.findCustomers(customerIds);
        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);
        Map<Long, LocalDate> earliestDeliveryDates = findEarliestDeliveryDates(salesOrders);

        return salesOrders.map(salesOrder -> {
            Customer customer = customers.get(salesOrder.getCustomerId());

            return new SalesOrderSummaryResponse(
                    salesOrder.getId(),
                    salesOrder.getSoNumber(),
                    salesOrder.getCustomerId(),
                    customer == null ? null : customer.getCustomerCode(),
                    customer == null ? null : customer.getName(),
                    salesOrder.getCustomerOrderNumber(),
                    salesOrder.getOrderDate(),
                    salesOrder.getResponsibleSalesUserId(),
                    userNames.get(salesOrder.getResponsibleSalesUserId()),
                    salesOrder.getStatus(),
                    earliestDeliveryDates.getOrDefault(salesOrder.getId(), salesOrder.getRequestedDeliveryDate()),
                    salesOrder.getTotalAmount(),
                    salesOrder.getCreatedAt(),
                    salesOrder.getVersion()
            );
        });
    }

    //清單顯示最早的實際交期：明細有填用明細，沒填用表頭；送審後明細的快照一定有值
    private Map<Long, LocalDate> findEarliestDeliveryDates(Page<SalesOrder> salesOrders) {
        List<Long> salesOrderIds = salesOrders.map(SalesOrder::getId).getContent();
        if (salesOrderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, LocalDate> earliest = new HashMap<>();
        salesOrderLineRepository.findAllBySalesOrderIdInOrderByLineNoAsc(salesOrderIds).forEach(line -> {
            SalesOrder salesOrder = line.getSalesOrder();
            LocalDate date = line.getDeliveryDate() != null
                    ? line.getDeliveryDate()
                    : line.resolveDeliveryDate(salesOrder.getRequestedDeliveryDate());
            if (date != null) {
                earliest.merge(salesOrder.getId(), date, (a, b) -> a.isBefore(b) ? a : b);
            }
        });

        return earliest;
    }

    public SalesOrderDetailResponse toDetail(SalesOrder salesOrder) {
        Customer customer = salesOrderMasterData.findCustomer(salesOrder.getCustomerId());

        List<SalesOrderLine> lines =
                salesOrderLineRepository.findAllBySalesOrderIdOrderByLineNoAsc(salesOrder.getId());

        List<String> warnings = new ArrayList<>();
        List<Line> lineResponses;
        SalesOrder.Amounts amounts;

        //放棄的草稿可能從未送審、沒有快照，所以和草稿一樣依目前主檔顯示，但不需要送審提示
        boolean preview = salesOrder.isDraftOrReturned() || salesOrder.getStatus() == SalesOrderStatus.CANCELLED;

        List<String> previewWarnings = salesOrder.isDraftOrReturned() ? warnings : new ArrayList<>();

        if (preview) {
            if (customer.getStatus() != MasterDataStatus.ACTIVE) {
                previewWarnings.add("客戶 " + customer.getCustomerCode() + " 已停用，無法送審");
            }
            lineResponses = lines.stream()
                    .map(line -> toPreviewLine(line, salesOrder, previewWarnings))
                    .toList();
            amounts = previewAmounts(lineResponses);
        } else {
            lineResponses = toSnapshotLines(salesOrder, lines);
            amounts = new SalesOrder.Amounts(
                    salesOrder.getUntaxedAmount(),
                    salesOrder.getTaxAmount(),
                    salesOrder.getTotalAmount()
            );
        }

        return new SalesOrderDetailResponse(
                salesOrder.getId(),
                salesOrder.getSoNumber(),
                salesOrder.getStatus(),
                salesOrder.getChannel(),
                new SalesOrderDetailResponse.CustomerRef(
                        customer.getId(),
                        customer.getCustomerCode(),
                        customer.getName()
                ),
                salesOrder.getCustomerOrderNumber(),
                salesOrder.getOrderDate(),
                userRef(salesOrder.getResponsibleSalesUserId()),
                userRef(salesOrder.getCreatedBy()),
                //草稿時顯示客戶目前的付款條件，送審後是快照
                preview ? customer.getPaymentTerm() : salesOrder.getPaymentTerm(),
                salesOrder.getRequestedDeliveryDate(),
                salesOrder.getDeliveryMethod(),
                salesOrder.getDeliveryAddress(),
                salesOrder.getContactName(),
                salesOrder.getContactPhone(),
                salesOrder.getRemark(),
                amounts == null ? null : amounts.untaxed(),
                amounts == null ? null : amounts.tax(),
                amounts == null ? null : amounts.total(),
                salesOrder.getSubmittedAt(),
                salesOrder.getApprovedAt(),
                salesOrder.getCreatedAt(),
                salesOrder.getUpdatedAt(),
                salesOrder.getVersion(),
                lineResponses,
                warnings
        );
    }

    //草稿試算：依目前的主檔計算基準數量與金額，同時列出送審會被擋下的問題
    private Line toPreviewLine(SalesOrderLine line, SalesOrder salesOrder, List<String> warnings) {
        LocalDate orderDate = salesOrder.getOrderDate();
        CustomerItemMapping mapping = salesOrderMasterData.findMapping(line.getCustomerItemMappingId());
        CompanyItem item = mapping.getCompanyItem();
        String prefix = "第 " + line.getLineNo() + " 行 " + mapping.getCustomerItemCode() + "：";

        if (!salesOrderMasterData.isUsable(mapping)) {
            warnings.add(prefix + "客戶料號對應、客戶或公司料號尚未啟用");
        }

        Optional<BigDecimal> factor = salesOrderMasterData.findConversionFactor(item, line.getUnit());
        if (factor.isEmpty()) {
            warnings.add(prefix + "公司料號沒有設定「" + line.getUnit().getDisplayName() + "」的換算");
        }

        //只能用有價格的單位下單，計價單位就是訂購單位；依訂單日期取價，和送審時相同
        Optional<ApplicablePrice> price = priceLookupService.findApplicablePrice(
                mapping.getId(),
                line.getUnit(),
                orderDate
        );
        if (price.isEmpty()) {
            warnings.add(prefix + "訂單日期沒有有效的「" + line.getUnit().getDisplayName() + "」價格，無法送審");
        }

        BigDecimal listUnitPrice = price.map(ApplicablePrice::unitPriceExcludingTax).orElse(null);
        if (listUnitPrice != null && line.isBelowListPrice(listUnitPrice)) {
            warnings.add(prefix + "單價 " + line.getRequestedUnitPrice().toPlainString()
                    + " 元低於底價 " + listUnitPrice.toPlainString() + " 元，無法送審");
        }
        BigDecimal unitPrice = line.dealUnitPrice(listUnitPrice);

        return new Line(
                line.getId(),
                line.getLineNo(),
                mapping.getId(),
                mapping.getCustomerItemCode(),
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getBaseUnit(),
                line.getQuantity(),
                line.getUnit(),
                factor.orElse(null),
                factor.map(f -> SalesOrderAmounts.baseQty(line.getQuantity(), f)).orElse(null),
                price.map(ApplicablePrice::source).orElse(null),
                price.map(ApplicablePrice::priceId).orElse(null),
                line.getRequestedUnitPrice(),
                listUnitPrice,
                unitPrice,
                unitPrice == null ? null : SalesOrderAmounts.lineAmount(line.getQuantity(), unitPrice),
                line.getRequestedDeliveryDate(),
                line.resolveDeliveryDate(salesOrder.getRequestedDeliveryDate()),
                null
        );
    }

    //每筆明細都有價格時才試算表頭金額
    private SalesOrder.Amounts previewAmounts(List<Line> lines) {
        if (lines.isEmpty() || lines.stream().anyMatch(line -> line.lineAmountExcludingTax() == null)) {
            return null;
        }

        return SalesOrderAmounts.documentAmounts(
                lines.stream().map(Line::lineAmountExcludingTax).toList()
        );
    }

    private List<Line> toSnapshotLines(SalesOrder salesOrder, List<SalesOrderLine> lines) {
        Map<Long, CompanyItem> items = salesOrderMasterData.findCompanyItems(
                lines.stream().map(SalesOrderLine::getCompanyItemId).toList()
        );

        boolean approved = salesOrder.getStatus() == SalesOrderStatus.APPROVED
                || salesOrder.getStatus() == SalesOrderStatus.CLOSED;

        Map<Long, Supply> supplies = approved ? findSupplies(lines) : Map.of();

        return lines.stream()
                .map(line -> {
                    CompanyItem item = items.get(line.getCompanyItemId());

                    return new Line(
                            line.getId(),
                            line.getLineNo(),
                            line.getCustomerItemMappingId(),
                            line.getCustomerItemCode(),
                            line.getCompanyItemId(),
                            item.getItemCode(),
                            item.getName(),
                            item.getBaseUnit(),
                            line.getQuantity(),
                            line.getUnit(),
                            line.getConversionFactor(),
                            line.getBaseQty(),
                            line.getPriceSource(),
                            line.getPriceId(),
                            line.getRequestedUnitPrice(),
                            line.getListUnitPrice(),
                            line.getUnitPriceExcludingTax(),
                            line.getLineAmountExcludingTax(),
                            line.getRequestedDeliveryDate(),
                            line.getDeliveryDate(),
                            supplies.get(line.getId())
                    );
                }).toList();
    }

    /**
     * 已核准 SO 明細的供應狀態；SO 詳情與缺料清單共用，兩邊的尚待規劃才會一致
     *
     * @param lines 已核准或已結案 SO 的明細
     * @return SO 明細 ID → 供應狀態
     */
    public Map<Long, Supply> findSupplies(List<SalesOrderLine> lines) {
        List<Long> lineIds = lines.stream().map(SalesOrderLine::getId).toList();
        if (lineIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> reservedByLine = inventoryService.findOpenQtyBySalesOrderLines(lineIds);
        Map<Long, BigDecimal> cancelledByLine = salesOrderQuantities.findCancelledQty(lineIds);
        Map<Long, PurchaseSupply> purchaseByLine = purchaseSupplyProvider.findBySalesOrderLines(lineIds);
        Map<Long, ShipmentQuantity> shipmentsByLine = salesOrderQuantities.findShipments(lineIds);

        Map<Long, Supply> supplies = new HashMap<>();
        for (SalesOrderLine line : lines) {
            supplies.put(line.getId(), toSupply(line, reservedByLine, cancelledByLine, purchaseByLine, shipmentsByLine));
        }

        return supplies;
    }

    /**
     * 缺料清單：只留尚待規劃大於 0 的明細，依偏好供應商篩選
     *
     * @param lines               已核准 SO 的明細，已依交期排序
     * @param preferredSupplierId 偏好供應商篩選，null 代表不篩選
     * @return 缺料明細
     */
    public List<SalesOrderShortageResponse> toShortages(List<SalesOrderLine> lines, Long preferredSupplierId) {
        Map<Long, Supply> supplies = findSupplies(lines);
        Map<Long, CompanyItem> items = salesOrderMasterData.findCompanyItems(
                lines.stream().map(SalesOrderLine::getCompanyItemId).distinct().toList()
        );

        List<SalesOrderLine> shortageLines = lines.stream()
                .filter(line -> supplies.get(line.getId()).unplannedQty().signum() > 0)
                .filter(line -> preferredSupplierId == null
                        || preferredSupplierId.equals(items.get(line.getCompanyItemId()).getPreferredSupplierId()))
                .toList();

        Map<Long, Customer> customers = salesOrderMasterData.findCustomers(
                shortageLines.stream().map(line -> line.getSalesOrder().getCustomerId()).distinct().toList()
        );
        Map<Long, Supplier> suppliers = salesOrderMasterData.findSuppliers(
                items.values().stream()
                        .map(CompanyItem::getPreferredSupplierId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        return shortageLines.stream()
                .map(line -> {
                    SalesOrder salesOrder = line.getSalesOrder();
                    Customer customer = customers.get(salesOrder.getCustomerId());
                    CompanyItem item = items.get(line.getCompanyItemId());
                    Supplier supplier = item.getPreferredSupplierId() == null
                            ? null
                            : suppliers.get(item.getPreferredSupplierId());

                    return new SalesOrderShortageResponse(
                            salesOrder.getId(),
                            salesOrder.getSoNumber(),
                            line.getId(),
                            line.getLineNo(),
                            customer.getCustomerCode(),
                            customer.getName(),
                            item.getId(),
                            item.getItemCode(),
                            item.getName(),
                            item.getBaseUnit(),
                            supplies.get(line.getId()).unplannedQty(),
                            line.getDeliveryDate(),
                            supplier == null ? null : supplier.getId(),
                            supplier == null ? null : supplier.getSupplierCode(),
                            supplier == null ? null : supplier.getName()
                    );
                })
                .toList();
    }

    /**
     * 尚待規劃 ＝ 有效訂購量 － 已出貨 － 已預留 － 已規劃未下單 － 採購在途，最低為 0
     * 有效訂購量 ＝ 訂購基準數量 － 已核准取消量；已規劃未下單與採購在途由採購模組提供，已出貨與進行中 DO 由出貨模組提供
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
        BigDecimal plannedNotOrderedQty = purchase.plannedNotOrderedQty();
        BigDecimal inTransitQty = purchase.inTransitQty();
        ShipmentQuantity shipment = shipmentsByLine.getOrDefault(line.getId(), ShipmentQuantity.NONE);
        BigDecimal shippedQty = shipment.shippedQty();
        BigDecimal cancelledQty = cancelledByLine.getOrDefault(line.getId(), BigDecimal.ZERO);

        BigDecimal unplannedQty = line.getBaseQty()
                .subtract(cancelledQty)
                .subtract(shippedQty)
                .subtract(reservedQty)
                .subtract(plannedNotOrderedQty)
                .subtract(inTransitQty)
                .max(BigDecimal.ZERO);

        return new Supply(
                reservedQty,
                plannedNotOrderedQty,
                inTransitQty,
                shipment.deliveringQty(),
                shippedQty,
                cancelledQty,
                unplannedQty,
                purchase.purchaseOrders()
        );
    }

    private SalesOrderDetailResponse.UserRef userRef(Long userId) {
        return new SalesOrderDetailResponse.UserRef(userId, userDirectoryService.findName(userId));
    }
}
