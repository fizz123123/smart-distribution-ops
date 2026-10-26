package com.smartdistribution.sales.mapper;

import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse.Line;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse.Supply;
import com.smartdistribution.sales.dto.response.SalesOrderSummaryResponse;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderMasterData;
import com.smartdistribution.sales.service.SalesOrderQuantities;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final UserDirectoryService userDirectoryService;
    private final InventoryService inventoryService;
    private final SalesOrderQuantities salesOrderQuantities;

    public Page<SalesOrderSummaryResponse> toSummaries(Page<SalesOrder> salesOrders) {
        Set<Long> customerIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        salesOrders.forEach(salesOrder -> {
            customerIds.add(salesOrder.getCustomerId());
            userIds.add(salesOrder.getResponsibleSalesUserId());
        });

        Map<Long, Customer> customers = salesOrderMasterData.findCustomers(customerIds);
        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);

        return salesOrders.map(salesOrder -> {
            Customer customer = customers.get(salesOrder.getCustomerId());

            return new SalesOrderSummaryResponse(
                    salesOrder.getId(),
                    salesOrder.getSoNumber(),
                    salesOrder.getCustomerId(),
                    customer == null ? null : customer.getCustomerCode(),
                    customer == null ? null : customer.getName(),
                    salesOrder.getCustomerOrderNumber(),
                    salesOrder.getResponsibleSalesUserId(),
                    userNames.get(salesOrder.getResponsibleSalesUserId()),
                    salesOrder.getStatus(),
                    salesOrder.getRequestedDeliveryDate(),
                    salesOrder.getTotalAmount(),
                    salesOrder.getCreatedAt(),
                    salesOrder.getVersion()
            );
        });
    }

    public SalesOrderDetailResponse toDetail(SalesOrder salesOrder) {
        Customer customer = salesOrderMasterData.findCustomer(salesOrder.getCustomerId());

        //負責業務和建立者常是同一人，Set.of 遇到重複會丟例外，所以用 HashSet
        Map<Long, String> userNames = userDirectoryService.findNamesByIds(
                new HashSet<>(List.of(salesOrder.getResponsibleSalesUserId(), salesOrder.getCreatedBy()))
        );

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
            lineResponses = lines.stream().map(line -> toPreviewLine(line, previewWarnings)).toList();
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
                userRef(salesOrder.getResponsibleSalesUserId(), userNames),
                userRef(salesOrder.getCreatedBy(), userNames),
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
    private Line toPreviewLine(SalesOrderLine line, List<String> warnings) {
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

        Optional<CustomerPrice> price = salesOrderMasterData.findActivePrice(mapping.getId(), line.getUnit());
        if (price.isEmpty()) {
            warnings.add(prefix + "尚無已覆核的「" + line.getUnit().getDisplayName() + "」價格，無法送審");
        }

        BigDecimal unitPrice = price.map(CustomerPrice::getUnitPriceExcludingTax).orElse(null);

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
                price.map(CustomerPrice::getId).orElse(null),
                unitPrice,
                unitPrice == null ? null : SalesOrderAmounts.lineAmount(line.getQuantity(), unitPrice),
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

        List<Long> lineIds = lines.stream().map(SalesOrderLine::getId).toList();

        Map<Long, BigDecimal> reservedByLine = approved
                ? inventoryService.findOpenQtyBySalesOrderLines(lineIds)
                : Map.of();

        Map<Long, BigDecimal> cancelledByLine = approved
                ? salesOrderQuantities.findCancelledQty(lineIds)
                : Map.of();

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
                            line.getCustomerPriceId(),
                            line.getUnitPriceExcludingTax(),
                            line.getLineAmountExcludingTax(),
                            approved ? toSupply(line, reservedByLine, cancelledByLine) : null
                    );
                }).toList();
    }

    /**
     * 尚待規劃 ＝ 有效訂購量 － 已出貨 － 已預留 － 已規劃未下單 － 採購在途，最低為 0
     * 有效訂購量 ＝ 訂購基準數量 － 已核准取消量；PO、DO 完成前，規劃、在途與出貨先固定為 0
     */
    private Supply toSupply(
            SalesOrderLine line,
            Map<Long, BigDecimal> reservedByLine,
            Map<Long, BigDecimal> cancelledByLine
    ) {
        BigDecimal reservedQty = reservedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
        BigDecimal plannedNotOrderedQty = BigDecimal.ZERO;
        BigDecimal inTransitQty = BigDecimal.ZERO;
        BigDecimal shippedQty = salesOrderQuantities.shippedQty(line);
        BigDecimal cancelledQty = cancelledByLine.getOrDefault(line.getId(), BigDecimal.ZERO);

        BigDecimal unplannedQty = line.getBaseQty()
                .subtract(cancelledQty)
                .subtract(shippedQty)
                .subtract(reservedQty)
                .subtract(plannedNotOrderedQty)
                .subtract(inTransitQty)
                .max(BigDecimal.ZERO);

        return new Supply(reservedQty, plannedNotOrderedQty, inTransitQty, shippedQty, cancelledQty, unplannedQty);
    }

    private SalesOrderDetailResponse.UserRef userRef(Long userId, Map<Long, String> userNames) {
        return new SalesOrderDetailResponse.UserRef(userId, userNames.get(userId));
    }
}
