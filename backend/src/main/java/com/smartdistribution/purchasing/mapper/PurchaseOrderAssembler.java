package com.smartdistribution.purchasing.mapper;

import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse.Line;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse.SalesOrderSource;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderSummaryResponse;
import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.repository.GoodsReceiptRepository;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.service.PurchaseOrderAmounts;
import com.smartdistribution.purchasing.service.PurchaseOrderMasterData;
import com.smartdistribution.sales.service.SalesOrderDemandService;
import com.smartdistribution.sales.service.SalesOrderLineDemand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 組出 PO 的回應，需要查供應商、料號、使用者與來源 SO，所以不是單純的欄位轉換
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderAssembler {

    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderMasterData purchaseOrderMasterData;
    private final SalesOrderDemandService salesOrderDemandService;
    private final UserDirectoryService userDirectoryService;

    //清單頁一次查好這一頁的供應商與建立者姓名
    public Page<PurchaseOrderSummaryResponse> toSummaries(Page<PurchaseOrder> purchaseOrders) {
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        purchaseOrders.forEach(purchaseOrder -> {
            supplierIds.add(purchaseOrder.getSupplierId());
            userIds.add(purchaseOrder.getCreatedBy());
        });

        Map<Long, Supplier> suppliers = purchaseOrderMasterData.findSuppliers(supplierIds);
        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);

        return purchaseOrders.map(purchaseOrder -> {
            Supplier supplier = suppliers.get(purchaseOrder.getSupplierId());

            return new PurchaseOrderSummaryResponse(
                    purchaseOrder.getId(),
                    purchaseOrder.getPoNumber(),
                    purchaseOrder.getSupplierId(),
                    supplier == null ? null : supplier.getSupplierCode(),
                    supplier == null ? null : supplier.getName(),
                    purchaseOrder.getStatus(),
                    purchaseOrder.getExpectedArrivalDate(),
                    purchaseOrder.getUntaxedAmount(),
                    purchaseOrder.getCreatedBy(),
                    userNames.get(purchaseOrder.getCreatedBy()),
                    purchaseOrder.getCreatedAt(),
                    purchaseOrder.getVersion()
            );
        });
    }

    public PurchaseOrderDetailResponse toDetail(PurchaseOrder purchaseOrder) {
        Supplier supplier = purchaseOrderMasterData.findSupplier(purchaseOrder.getSupplierId());
        List<PurchaseOrderLine> lines =
                purchaseOrderLineRepository.findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrder.getId());

        //來源 SO 的編號與行號；一次查完這張 PO 關聯的所有 SO 明細
        Map<Long, SalesOrderLineDemand> sources = salesOrderDemandService.findDemands(
                lines.stream()
                        .map(PurchaseOrderLine::getSalesOrderLineId)
                        .filter(Objects::nonNull)
                        .toList()
        );

        //一般補庫明細沒有來源；先判斷再取值，不可變的 Map 不接受用 null 查詢
        List<Line> lineResponses = lines.stream()
                .map(line -> toLine(line, line.isLinkedToSalesOrder() ? sources.get(line.getSalesOrderLineId()) : null))
                .toList();

        return new PurchaseOrderDetailResponse(
                purchaseOrder.getId(),
                purchaseOrder.getPoNumber(),
                purchaseOrder.getStatus(),
                new PurchaseOrderDetailResponse.SupplierRef(
                        supplier.getId(),
                        supplier.getSupplierCode(),
                        supplier.getName()
                ),
                purchaseOrder.getExpectedArrivalDate(),
                purchaseOrder.getRemark(),
                untaxedAmount(purchaseOrder, lines),
                new PurchaseOrderDetailResponse.UserRef(
                        purchaseOrder.getCreatedBy(),
                        userDirectoryService.findName(purchaseOrder.getCreatedBy())
                ),
                purchaseOrder.getSubmittedAt(),
                purchaseOrder.getApprovedAt(),
                purchaseOrder.getCreatedAt(),
                purchaseOrder.getUpdatedAt(),
                purchaseOrder.getVersion(),
                lineResponses,
                goodsReceiptRepository.findAllByPurchaseOrderIdOrderByIdAsc(purchaseOrder.getId())
                        .stream()
                        .map(receipt -> new PurchaseOrderDetailResponse.ReceiptRef(
                                receipt.getId(),
                                receipt.getReceiptNumber(),
                                receipt.getReceivedDate()
                        ))
                        .toList()
        );
    }

    private Line toLine(PurchaseOrderLine line, SalesOrderLineDemand source) {
        //單張 PO 的明細有限，直接逐筆查料號
        CompanyItem item = purchaseOrderMasterData.findCompanyItem(line.getCompanyItemId());

        return new Line(
                line.getId(),
                line.getLineNo(),
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getBaseUnit(),
                source == null ? null : new SalesOrderSource(
                        source.salesOrderLineId(),
                        source.salesOrderId(),
                        source.salesOrderNumber(),
                        source.lineNo()
                ),
                line.getQuantity(),
                line.getUnit(),
                line.getConversionFactor(),
                line.getBaseQty(),
                line.getUnitPriceExcludingTax(),
                line.getLineAmountExcludingTax(),
                line.getAcceptedBaseQty(),
                line.openBaseQty(),
                line.getStatus()
        );
    }

    //送審後用保存的金額；草稿在每筆明細都有單價時才試算
    private BigDecimal untaxedAmount(PurchaseOrder purchaseOrder, List<PurchaseOrderLine> lines) {
        if (purchaseOrder.getUntaxedAmount() != null) {
            return purchaseOrder.getUntaxedAmount();
        }

        if (lines.isEmpty() || lines.stream().anyMatch(line -> line.getLineAmountExcludingTax() == null)) {
            return null;
        }

        return PurchaseOrderAmounts.untaxedTotal(
                lines.stream().map(PurchaseOrderLine::getLineAmountExcludingTax).toList()
        );
    }
}
