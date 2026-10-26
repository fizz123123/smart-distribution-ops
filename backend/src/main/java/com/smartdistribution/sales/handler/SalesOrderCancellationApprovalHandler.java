package com.smartdistribution.sales.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.inventory.service.ReservationDemand;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderCancellation;
import com.smartdistribution.sales.entity.SalesOrderCancellationLine;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.repository.SalesOrderCancellationLineRepository;
import com.smartdistribution.sales.repository.SalesOrderCancellationRepository;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.service.SalesOrderQuantities;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SalesOrderCancellationApprovalHandler implements ApprovalHandler {

    private final SalesOrderCancellationRepository salesOrderCancellationRepository;
    private final SalesOrderCancellationLineRepository salesOrderCancellationLineRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderQuantities salesOrderQuantities;
    private final InventoryService inventoryService;

    @Override
    public ApprovalTargetType targetType() {
        return ApprovalTargetType.SO_CANCELLATION;
    }

    //顯示名稱範例：SOC-000001 SO-000001 取消 2 筆明細
    @Override
    public ApprovalTarget loadTarget(Long targetId) {
        SalesOrderCancellation cancellation = findCancellation(targetId);
        int lineCount = findLines(targetId).size();

        String label = cancellation.getCancellationNumber() + " "
                + cancellation.getSalesOrder().getSoNumber()
                + " 取消 " + lineCount + " 筆明細";

        return new ApprovalTarget(cancellation.getCreatedBy(), label, List.of());
    }

    /**
     * 核准餘量取消：
     * 1. SO 仍須是已核准；取消量重新檢查，不可超過可取消量
     * 2. 先取消尚未預留的部分，只有預留量超過取消後的剩餘需求時才釋放預留
     * 3. 所有明細都已沒有可取消的餘量時，SO 結案
     * 任何一步失敗，核准框架會讓整個核准回滾，不留下部分釋放
     */
    @Override
    public void onApproved(Long targetId, Long approverId, Long approvalRequestId) {
        SalesOrderCancellation cancellation = findCancellation(targetId);
        SalesOrder salesOrder = cancellation.getSalesOrder();

        if (salesOrder.getStatus() != SalesOrderStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SO_STATUS_CONFLICT);
        }

        List<SalesOrderLine> salesOrderLines =
                salesOrderLineRepository.findAllBySalesOrderIdOrderByLineNoAsc(salesOrder.getId());
        List<SalesOrderCancellationLine> cancellationLines = findLines(targetId);

        //此時只會算到其他已核准的取消，本張尚未核准
        Map<Long, BigDecimal> cancelledBefore = salesOrderQuantities.findCancelledQty(
                salesOrderLines.stream().map(SalesOrderLine::getId).toList()
        );
        Map<Long, BigDecimal> cancelledAfter = new HashMap<>(cancelledBefore);

        for (SalesOrderCancellationLine line : cancellationLines) {
            SalesOrderLine salesOrderLine = line.getSalesOrderLine();
            salesOrderQuantities.ensureWithinCancellableQty(salesOrderLine, line.getBaseQty(), cancelledBefore);

            cancelledAfter.merge(salesOrderLine.getId(), line.getBaseQty(), BigDecimal::add);
        }

        inventoryService.releaseForCancellation(
                cancellationLines.stream()
                        .map(line -> new ReservationDemand(
                                line.getSalesOrderLine().getId(),
                                line.getSalesOrderLine().getCompanyItemId(),
                                salesOrderQuantities.cancellableQty(line.getSalesOrderLine(), cancelledAfter)
                        ))
                        .toList(),
                cancellation.getId(),
                approverId
        );

        cancellation.approve(Instant.now());

        boolean nothingRemaining = salesOrderLines.stream()
                .allMatch(line -> salesOrderQuantities.cancellableQty(line, cancelledAfter).signum() <= 0);
        if (nothingRemaining) {
            salesOrder.close();
        }
    }

    @Override
    public void onReturned(Long targetId, Long approverId) {
        findCancellation(targetId).returnForRevision();
    }

    private SalesOrderCancellation findCancellation(Long cancellationId) {
        return salesOrderCancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_CANCELLATION_NOT_FOUND));
    }

    private List<SalesOrderCancellationLine> findLines(Long cancellationId) {
        return salesOrderCancellationLineRepository.findAllByCancellationIdOrderByIdAsc(cancellationId);
    }
}
