package com.smartdistribution.purchasing.mapper;

import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptResponse;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptSummaryResponse;
import com.smartdistribution.purchasing.dto.response.OpenPurchaseOrderResponse;
import com.smartdistribution.purchasing.entity.GoodsReceipt;
import com.smartdistribution.purchasing.entity.GoodsReceiptLine;
import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.repository.GoodsReceiptLineRepository;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.service.PurchaseOrderMasterData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 組出收貨相關的回應；給倉管看的內容都不含單價
 */
@Component
@RequiredArgsConstructor
public class GoodsReceiptAssembler {

    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderMasterData purchaseOrderMasterData;
    private final UserDirectoryService userDirectoryService;

    //清單頁一次查好這一頁的 PO 明細、供應商與料號
    public Page<OpenPurchaseOrderResponse> toOpenPurchaseOrders(Page<PurchaseOrder> purchaseOrders) {
        Map<Long, List<PurchaseOrderLine>> linesByOrder = purchaseOrderLineRepository
                .findAllByPurchaseOrderIdInOrderByLineNoAsc(
                        purchaseOrders.stream().map(PurchaseOrder::getId).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(line -> line.getPurchaseOrder().getId()));

        Map<Long, Supplier> suppliers = purchaseOrderMasterData.findSuppliers(
                purchaseOrders.stream().map(PurchaseOrder::getSupplierId).collect(Collectors.toSet())
        );
        Map<Long, CompanyItem> items = purchaseOrderMasterData.findCompanyItems(
                linesByOrder.values().stream()
                        .flatMap(List::stream)
                        .map(PurchaseOrderLine::getCompanyItemId)
                        .collect(Collectors.toSet())
        );

        return purchaseOrders.map(purchaseOrder -> {
            Supplier supplier = suppliers.get(purchaseOrder.getSupplierId());
            List<OpenPurchaseOrderResponse.Line> lines = linesByOrder
                    .getOrDefault(purchaseOrder.getId(), List.of())
                    .stream()
                    .map(line -> toOpenLine(line, items.get(line.getCompanyItemId())))
                    .toList();

            return new OpenPurchaseOrderResponse(
                    purchaseOrder.getId(),
                    purchaseOrder.getPoNumber(),
                    purchaseOrder.getStatus(),
                    supplier == null ? null : supplier.getSupplierCode(),
                    supplier == null ? null : supplier.getName(),
                    purchaseOrder.getExpectedArrivalDate(),
                    lines
            );
        });
    }

    //清單頁一次查好這一頁的供應商與過帳人姓名
    public Page<GoodsReceiptSummaryResponse> toSummaries(Page<GoodsReceipt> receipts) {
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        receipts.forEach(receipt -> {
            supplierIds.add(receipt.getPurchaseOrder().getSupplierId());
            userIds.add(receipt.getPostedBy());
        });

        Map<Long, Supplier> suppliers = purchaseOrderMasterData.findSuppliers(supplierIds);
        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);

        return receipts.map(receipt -> {
            PurchaseOrder purchaseOrder = receipt.getPurchaseOrder();
            Supplier supplier = suppliers.get(purchaseOrder.getSupplierId());

            return new GoodsReceiptSummaryResponse(
                    receipt.getId(),
                    receipt.getReceiptNumber(),
                    purchaseOrder.getId(),
                    purchaseOrder.getPoNumber(),
                    supplier == null ? null : supplier.getName(),
                    receipt.getReceivedDate(),
                    userNames.get(receipt.getPostedBy()),
                    receipt.getPostedAt()
            );
        });
    }

    public GoodsReceiptResponse toResponse(GoodsReceipt receipt) {
        PurchaseOrder purchaseOrder = receipt.getPurchaseOrder();
        Supplier supplier = purchaseOrderMasterData.findSupplier(purchaseOrder.getSupplierId());

        List<GoodsReceiptResponse.Line> lines = goodsReceiptLineRepository
                .findAllByGoodsReceiptIdOrderByIdAsc(receipt.getId())
                .stream()
                .map(this::toLine)
                .toList();

        return new GoodsReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                purchaseOrder.getId(),
                purchaseOrder.getPoNumber(),
                supplier.getSupplierCode(),
                supplier.getName(),
                receipt.getReceivedDate(),
                receipt.getPostedBy(),
                userDirectoryService.findName(receipt.getPostedBy()),
                receipt.getPostedAt(),
                lines
        );
    }

    private GoodsReceiptResponse.Line toLine(GoodsReceiptLine line) {
        PurchaseOrderLine purchaseOrderLine = line.getPurchaseOrderLine();
        CompanyItem item = purchaseOrderMasterData.findCompanyItem(purchaseOrderLine.getCompanyItemId());

        return new GoodsReceiptResponse.Line(
                line.getId(),
                purchaseOrderLine.getId(),
                purchaseOrderLine.getLineNo(),
                item.getId(),
                item.getItemCode(),
                item.getName(),
                purchaseOrderLine.getUnit(),
                line.getReceivedQty(),
                line.getAcceptedQty(),
                line.getDamagedQty(),
                line.getNonconformingQty(),
                line.getAcceptedBaseQty(),
                line.getExceptionReason()
        );
    }

    private OpenPurchaseOrderResponse.Line toOpenLine(PurchaseOrderLine line, CompanyItem item) {
        return new OpenPurchaseOrderResponse.Line(
                line.getId(),
                line.getLineNo(),
                line.getCompanyItemId(),
                item == null ? null : item.getItemCode(),
                item == null ? null : item.getName(),
                line.getUnit(),
                line.getQuantity(),
                toOrderUnit(line.getAcceptedBaseQty(), line),
                toOrderUnit(line.openBaseQty(), line)
        );
    }

    //基準數量換回 PO 明細的單位，小數位依單位
    private static BigDecimal toOrderUnit(BigDecimal baseQty, PurchaseOrderLine line) {
        return baseQty.divide(line.getConversionFactor(), line.getUnit().getDecimalPlaces(), RoundingMode.HALF_UP);
    }
}
