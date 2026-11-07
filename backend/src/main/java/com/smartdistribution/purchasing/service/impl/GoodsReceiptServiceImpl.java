package com.smartdistribution.purchasing.service.impl;

import com.smartdistribution.inventory.service.InventoryService.ReceiptLine;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.purchasing.dto.request.GoodsReceiptLineRequest;
import com.smartdistribution.purchasing.dto.request.PostGoodsReceiptRequest;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptResponse;
import com.smartdistribution.purchasing.dto.response.GoodsReceiptSummaryResponse;
import com.smartdistribution.purchasing.dto.response.OpenPurchaseOrderResponse;
import com.smartdistribution.purchasing.entity.GoodsReceipt;
import com.smartdistribution.purchasing.entity.GoodsReceiptLine;
import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.purchasing.mapper.GoodsReceiptAssembler;
import com.smartdistribution.purchasing.repository.GoodsReceiptLineRepository;
import com.smartdistribution.purchasing.repository.GoodsReceiptRepository;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.repository.PurchaseOrderRepository;
import com.smartdistribution.purchasing.service.GoodsReceiptService;
import com.smartdistribution.purchasing.service.PurchaseOrderAmounts;
import com.smartdistribution.purchasing.service.PurchaseOrderMasterData;
import com.smartdistribution.sales.service.SalesOrderDemandService.SalesOrderLineDemand;
import com.smartdistribution.sales.service.SalesOrderDemandService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private static final List<PurchaseOrderStatus> RECEIVABLE_STATUSES = List.of(
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderMasterData purchaseOrderMasterData;
    private final GoodsReceiptAssembler goodsReceiptAssembler;
    private final SalesOrderDemandService salesOrderDemandService;
    private final InventoryService inventoryService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('GOODS_RECEIPT_VIEW')")
    public PageResponse<OpenPurchaseOrderResponse> findOpenPurchaseOrders(Pageable pageable) {
        return PageResponse.from(goodsReceiptAssembler.toOpenPurchaseOrders(
                purchaseOrderRepository.findAllByStatusIn(RECEIVABLE_STATUSES, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('GOODS_RECEIPT_VIEW')")
    public PageResponse<GoodsReceiptSummaryResponse> findGoodsReceipts(Long purchaseOrderId, Pageable pageable) {
        return PageResponse.from(goodsReceiptAssembler.toSummaries(
                goodsReceiptRepository.search(purchaseOrderId, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('GOODS_RECEIPT_VIEW')")
    public GoodsReceiptResponse findGoodsReceipt(Long goodsReceiptId) {
        GoodsReceipt receipt = goodsReceiptRepository.findById(goodsReceiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_RECEIPT_NOT_FOUND));

        return goodsReceiptAssembler.toResponse(receipt);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('GOODS_RECEIPT_POST')")
    public GoodsReceiptResponse postGoodsReceipt(Long purchaseOrderId, PostGoodsReceiptRequest request) {
        //先鎖 PO 表頭，再由庫存模組依料號順序鎖餘額列；所有收貨都照這個順序取鎖
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_ORDER_NOT_FOUND));

        if (!purchaseOrder.canReceive()) {
            throw new BusinessException(ErrorCode.PO_STATUS_CONFLICT);
        }

        Map<Long, PurchaseOrderLine> purchaseOrderLines = purchaseOrderLineRepository
                .findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrderId)
                .stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));

        List<CheckedReceipt> checkedReceipts = checkLines(request.lines(), purchaseOrderLines);

        Long actorId = currentUserProvider.getCurrentUserId();
        GoodsReceipt receipt = new GoodsReceipt(purchaseOrder, request.receivedDate(), actorId, Instant.now());

        //收貨單號依 ID 產生，所以要先 insert 取得 id 再回填
        goodsReceiptRepository.saveAndFlush(receipt);
        receipt.assignNumber();

        List<GoodsReceiptLine> receiptLines = new ArrayList<>();
        for (CheckedReceipt checked : checkedReceipts) {
            GoodsReceiptLine receiptLine = goodsReceiptLineRepository.save(new GoodsReceiptLine(
                    receipt,
                    checked.purchaseOrderLine(),
                    checked.quantities(),
                    checked.acceptedBaseQty(),
                    checked.exceptionReason()
            ));
            receiptLines.add(receiptLine);
            checked.purchaseOrderLine().receive(checked.acceptedBaseQty());
        }

        inventoryService.receiveForPurchaseOrder(toInventoryLines(receiptLines), actorId);

        boolean allLinesDone = purchaseOrderLines.values().stream().noneMatch(PurchaseOrderLine::isOpen);
        purchaseOrder.recordReceipt(allLinesDone);

        return goodsReceiptAssembler.toResponse(receipt);
    }

    /**
     * 收貨明細的檢查，錯誤訊息帶行號：
     * 1. 每筆都是這張 PO 尚未結案的明細，而且只出現一次
     * 2. 小數位符合 PO 明細的單位；合格 ＋ 損壞 ＋ 規格不符 ＝ 實收；有異常數量時要填原因
     * 3. 實收不超過未結量
     */
    private List<CheckedReceipt> checkLines(
            List<GoodsReceiptLineRequest> lines,
            Map<Long, PurchaseOrderLine> purchaseOrderLines
    ) {
        List<CheckedReceipt> checkedReceipts = new ArrayList<>();
        Set<Long> seenLineIds = new HashSet<>();

        for (GoodsReceiptLineRequest line : lines) {
            PurchaseOrderLine purchaseOrderLine = purchaseOrderLines.get(line.purchaseOrderLineId());

            if (purchaseOrderLine == null || !seenLineIds.add(line.purchaseOrderLineId())) {
                throw new BusinessException(ErrorCode.GOODS_RECEIPT_LINE_INVALID);
            }

            CompanyItem item = purchaseOrderMasterData.findCompanyItem(purchaseOrderLine.getCompanyItemId());
            String prefix = "第 " + purchaseOrderLine.getLineNo() + " 行 " + item.getItemCode() + "：";

            if (!purchaseOrderLine.isOpen()) {
                throw new BusinessException(ErrorCode.GOODS_RECEIPT_LINE_INVALID, prefix + "這筆明細已經結案，不能再收貨。");
            }

            GoodsReceiptLine.Quantities quantities = new GoodsReceiptLine.Quantities(
                    line.receivedQuantity(),
                    line.acceptedQuantity(),
                    line.damagedQuantity(),
                    line.nonconformingQuantity()
            );
            checkQuantities(quantities, purchaseOrderLine, line.exceptionReason(), prefix);

            BigDecimal receivedBaseQty = PurchaseOrderAmounts.baseQty(
                    line.receivedQuantity(), purchaseOrderLine.getConversionFactor()
            );
            if (receivedBaseQty.compareTo(purchaseOrderLine.openBaseQty()) > 0) {
                throw new BusinessException(
                        ErrorCode.GOODS_RECEIPT_QUANTITY_EXCEEDED,
                        prefix + "最多還能收 " + purchaseOrderLine.openBaseQty().stripTrailingZeros().toPlainString()
                                + "（庫存基準單位）。"
                );
            }

            checkedReceipts.add(new CheckedReceipt(
                    purchaseOrderLine,
                    quantities,
                    PurchaseOrderAmounts.baseQty(line.acceptedQuantity(), purchaseOrderLine.getConversionFactor()),
                    line.exceptionReason()
            ));
        }

        return checkedReceipts;
    }

    private void checkQuantities(
            GoodsReceiptLine.Quantities quantities,
            PurchaseOrderLine purchaseOrderLine,
            String exceptionReason,
            String prefix
    ) {
        int allowedScale = purchaseOrderLine.getUnit().getDecimalPlaces();
        boolean scaleInvalid = List.of(
                quantities.received(), quantities.accepted(), quantities.damaged(), quantities.nonconforming()
        ).stream().anyMatch(quantity -> quantity.stripTrailingZeros().scale() > allowedScale);
        if (scaleInvalid) {
            throw new BusinessException(
                    ErrorCode.GOODS_RECEIPT_QUANTITY_INVALID,
                    prefix + "「" + purchaseOrderLine.getUnit().getDisplayName() + "」最多 " + allowedScale + " 位小數。"
            );
        }

        BigDecimal exceptionQty = quantities.damaged().add(quantities.nonconforming());
        if (quantities.accepted().add(exceptionQty).compareTo(quantities.received()) != 0) {
            throw new BusinessException(
                    ErrorCode.GOODS_RECEIPT_QUANTITY_INVALID,
                    prefix + "合格、損壞與規格不符的加總要等於實收。"
            );
        }

        if (exceptionQty.signum() > 0 && (exceptionReason == null || exceptionReason.isBlank())) {
            throw new BusinessException(
                    ErrorCode.GOODS_RECEIPT_QUANTITY_INVALID,
                    prefix + "有損壞或規格不符時，請填寫原因。"
            );
        }
    }

    /**
     * 只有合格數量入庫；關聯 SO 的明細帶上來源的剩餘需求，讓庫存模組計算要補足多少預留
     */
    private List<ReceiptLine> toInventoryLines(List<GoodsReceiptLine> receiptLines) {
        Map<Long, SalesOrderLineDemand> demands = salesOrderDemandService.findDemands(
                receiptLines.stream()
                        .map(line -> line.getPurchaseOrderLine().getSalesOrderLineId())
                        .filter(Objects::nonNull)
                        .toList()
        );

        List<ReceiptLine> inventoryLines = new ArrayList<>();

        for (GoodsReceiptLine receiptLine : receiptLines) {
            if (receiptLine.getAcceptedBaseQty().signum() == 0) {
                continue;
            }

            PurchaseOrderLine purchaseOrderLine = receiptLine.getPurchaseOrderLine();
            //一般補庫沒有來源 SO，剩餘需求視為 0
            SalesOrderLineDemand demand = purchaseOrderLine.isLinkedToSalesOrder()
                    ? demands.get(purchaseOrderLine.getSalesOrderLineId())
                    : null;

            inventoryLines.add(new ReceiptLine(
                    receiptLine.getId(),
                    purchaseOrderLine.getCompanyItemId(),
                    receiptLine.getAcceptedBaseQty(),
                    purchaseOrderLine.getSalesOrderLineId(),
                    demand == null ? BigDecimal.ZERO : demand.remainingQty()
            ));
        }

        return inventoryLines;
    }

    /**
     * 檢查通過的一筆收貨
     */
    private record CheckedReceipt(
            PurchaseOrderLine purchaseOrderLine,
            GoodsReceiptLine.Quantities quantities,
            BigDecimal acceptedBaseQty,
            String exceptionReason
    ) {
    }
}
