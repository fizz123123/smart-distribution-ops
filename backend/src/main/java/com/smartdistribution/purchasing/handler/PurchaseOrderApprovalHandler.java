package com.smartdistribution.purchasing.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.document.entity.DocumentType;
import com.smartdistribution.document.service.DocumentService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.repository.PurchaseOrderRepository;
import com.smartdistribution.purchasing.service.PurchaseOrderMasterData;
import com.smartdistribution.purchasing.service.PurchaseOrderPdfLayout;
import com.smartdistribution.purchasing.service.PurchaseOrderQuantities;
import com.smartdistribution.sales.service.SalesOrderDemandService.SalesOrderLineDemand;
import com.smartdistribution.sales.service.SalesOrderDemandService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PurchaseOrderApprovalHandler implements ApprovalHandler {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderMasterData purchaseOrderMasterData;
    private final PurchaseOrderQuantities purchaseOrderQuantities;
    private final PurchaseOrderPdfLayout purchaseOrderPdfLayout;
    private final SalesOrderDemandService salesOrderDemandService;
    private final DocumentService documentService;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.PURCHASE_ORDER;
    }

    //顯示名稱範例：PO-000001 SUP-000004 大同管材 未稅 360 元
    //查看核准申請詳情時也會呼叫這裡；退回後修改的 PO 已清掉送審金額，此時不組金額，避免格式化 null 失敗
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        PurchaseOrder purchaseOrder = findPurchaseOrder(targetId);
        Supplier supplier = purchaseOrderMasterData.findSupplier(purchaseOrder.getSupplierId());

        String label = purchaseOrder.getPoNumber() + " " + supplier.getSupplierCode() + " " + supplier.getName();
        if (purchaseOrder.getUntaxedAmount() != null) {
            label += " 未稅 " + NumberFormat.getIntegerInstance(Locale.TAIWAN).format(purchaseOrder.getUntaxedAmount()) + " 元";
        }

        List<String> warnings = purchaseOrder.getStatus() == PurchaseOrderStatus.PENDING_APPROVAL
                ? buildWarnings(purchaseOrder, findLines(targetId))
                : List.of();

        return new ApprovalTarget(purchaseOrder.getCreatedBy(), label, warnings);
    }

    /**
     * 核准：
     * 1. 供應商與每筆明細的公司料號仍須啟用
     * 2. PO 轉為已核准，數量開始計入採購在途
     * 3. 產生正式 PDF 並登記
     * 來源 SO 需求變少不擋核准，只在核准前提示；任何一步失敗，整個核准回滾
     */
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        PurchaseOrder purchaseOrder = findPurchaseOrder(targetId);
        List<PurchaseOrderLine> lines = findLines(targetId);

        Supplier supplier = purchaseOrderMasterData.findSupplier(purchaseOrder.getSupplierId());
        if (supplier.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.PO_MASTER_DATA_INACTIVE,
                    "供應商 " + supplier.getSupplierCode() + " 已停用，無法核准。"
            );
        }

        for (PurchaseOrderLine line : lines) {
            CompanyItem item = purchaseOrderMasterData.findCompanyItem(line.getCompanyItemId());
            if (item.getStatus() != MasterDataStatus.ACTIVE) {
                throw new BusinessException(
                        ErrorCode.PO_MASTER_DATA_INACTIVE,
                        "第 " + line.getLineNo() + " 行 " + item.getItemCode() + "：公司料號已停用，無法核准。"
                );
            }
        }

        purchaseOrder.approve(Instant.now());

        byte[] pdf = purchaseOrderPdfLayout.render(purchaseOrder, supplier, lines);
        documentService.store(DocumentType.PURCHASE_ORDER, purchaseOrder.getId(), purchaseOrder.getPoNumber(), pdf, approverId);
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findPurchaseOrder(targetId).returnForRevision();
    }

    /**
     * 核准前給主管的提示，只是提醒，不會擋下核准
     */
    private List<String> buildWarnings(PurchaseOrder purchaseOrder, List<PurchaseOrderLine> lines) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(priceChangeWarnings(purchaseOrder, lines));
        warnings.addAll(reducedDemandWarnings(purchaseOrder, lines));
        return warnings;
    }

    //單價和同供應商、同料號、同單位最近一次核准的單價不同
    private List<String> priceChangeWarnings(PurchaseOrder purchaseOrder, List<PurchaseOrderLine> lines) {
        List<String> warnings = new ArrayList<>();

        for (PurchaseOrderLine line : lines) {
            List<BigDecimal> lastPrices = purchaseOrderLineRepository.findApprovedPrices(
                    purchaseOrder.getSupplierId(), line.getCompanyItemId(), line.getUnit(), PageRequest.of(0, 1)
            );

            if (!lastPrices.isEmpty() && lastPrices.getFirst().compareTo(line.getUnitPriceExcludingTax()) != 0) {
                warnings.add("第 " + line.getLineNo() + " 行：本次單價 "
                        + line.getUnitPriceExcludingTax().stripTrailingZeros().toPlainString()
                        + "，最近一次核准單價 " + lastPrices.getFirst().stripTrailingZeros().toPlainString());
            }
        }

        return warnings;
    }

    //送審後來源 SO 的需求變少，多出來的數量到貨後會成為一般庫存
    private List<String> reducedDemandWarnings(PurchaseOrder purchaseOrder, List<PurchaseOrderLine> lines) {
        Map<Long, SalesOrderLineDemand> demands = salesOrderDemandService.findDemands(
                lines.stream().map(PurchaseOrderLine::getSalesOrderLineId).filter(Objects::nonNull).toList()
        );
        //排除這張 PO 自己，才看得出扣掉它之後來源還缺多少
        Map<Long, BigDecimal> plannable = purchaseOrderQuantities.findPlannableQty(demands, purchaseOrder.getId());

        List<String> warnings = new ArrayList<>();

        for (PurchaseOrderLine line : lines) {
            //一般補庫沒有來源 SO，不會有需求變少的問題
            if (!line.isLinkedToSalesOrder()) {
                continue;
            }

            SalesOrderLineDemand demand = demands.get(line.getSalesOrderLineId());
            if (demand == null) {
                continue;
            }

            BigDecimal needed = plannable.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO);
            if (line.getBaseQty().compareTo(needed) > 0) {
                warnings.add("第 " + line.getLineNo() + " 行：來源 " + demand.salesOrderNumber()
                        + " 目前尚待規劃 " + needed.stripTrailingZeros().toPlainString()
                        + "，本次採購 " + line.getBaseQty().stripTrailingZeros().toPlainString()
                        + "，多出的 " + line.getBaseQty().subtract(needed).stripTrailingZeros().toPlainString()
                        + " 到貨後成為一般庫存");
            }
        }

        return warnings;
    }

    private PurchaseOrder findPurchaseOrder(Long purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }

    private List<PurchaseOrderLine> findLines(Long purchaseOrderId) {
        return purchaseOrderLineRepository.findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrderId);
    }
}
