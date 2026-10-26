package com.smartdistribution.sales.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.sales.dto.request.CancellationLineRequest;
import com.smartdistribution.sales.dto.request.CreateSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.SubmitSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.WithdrawSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.response.SalesOrderCancellationResponse;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderCancellation;
import com.smartdistribution.sales.entity.SalesOrderCancellationLine;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.mapper.SalesOrderCancellationMapper;
import com.smartdistribution.sales.repository.SalesOrderCancellationLineRepository;
import com.smartdistribution.sales.repository.SalesOrderCancellationRepository;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.repository.SalesOrderRepository;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderCancellationService;
import com.smartdistribution.sales.service.SalesOrderQuantities;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
public class SalesOrderCancellationServiceImpl implements SalesOrderCancellationService {

    private final SalesOrderCancellationRepository salesOrderCancellationRepository;
    private final SalesOrderCancellationLineRepository salesOrderCancellationLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderQuantities salesOrderQuantities;
    private final SalesOrderCancellationMapper salesOrderCancellationMapper;
    private final ApprovalService approvalService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('SO_VIEW')")
    public List<SalesOrderCancellationResponse> findCancellations(Long salesOrderId) {
        findSalesOrder(salesOrderId);

        return salesOrderCancellationRepository.findAllBySalesOrderIdOrderByIdDesc(salesOrderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasAuthority('SO_VIEW')")
    public SalesOrderCancellationResponse findCancellation(Long cancellationId) {
        return toResponse(findCancellationById(cancellationId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_CANCELLATION_CREATE')")
    public SalesOrderCancellationResponse createCancellation(
            Long salesOrderId,
            CreateSalesOrderCancellationRequest request
    ) {
        SalesOrder salesOrder = findSalesOrder(salesOrderId);
        ensureCancellable(salesOrder);

        if (salesOrderCancellationRepository.existsBySalesOrderIdAndStatusIn(
                salesOrderId,
                SalesOrderCancellation.OPEN_STATUSES
        )) {
            throw new BusinessException(ErrorCode.SO_CANCELLATION_PENDING_EXISTS);
        }

        List<DraftLine> lines = validateLines(salesOrder, request.lines());

        SalesOrderCancellation cancellation = new SalesOrderCancellation(
                salesOrder,
                request.reason(),
                currentUserProvider.getCurrentUserId()
        );

        //編號依 ID 產生，所以要先 insert 取得 id 再回填
        salesOrderCancellationRepository.saveAndFlush(cancellation);
        cancellation.assignNumber();
        saveLines(cancellation, lines);
        salesOrderCancellationRepository.flush();

        return toResponse(cancellation);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_CANCELLATION_CREATE')")
    public SalesOrderCancellationResponse updateCancellation(
            Long cancellationId,
            UpdateSalesOrderCancellationRequest request
    ) {
        SalesOrderCancellation cancellation = findCancellationById(cancellationId);
        verifyVersion(cancellation, request.version());

        if (!cancellation.isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.SO_CANCELLATION_STATUS_CONFLICT);
        }

        SalesOrder salesOrder = cancellation.getSalesOrder();
        ensureCancellable(salesOrder);
        List<DraftLine> lines = validateLines(salesOrder, request.lines());

        //明細整批取代：先刪除舊明細再寫入，做法同 SO 草稿
        salesOrderCancellationLineRepository.deleteAllByCancellationId(cancellationId);
        saveLines(cancellation, lines);
        cancellation.updateDraft(request.reason(), Instant.now());
        salesOrderCancellationRepository.flush();

        return toResponse(cancellation);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_CANCELLATION_SUBMIT')")
    public SalesOrderCancellationResponse submitCancellation(
            Long cancellationId,
            SubmitSalesOrderCancellationRequest request
    ) {
        SalesOrderCancellation cancellation = findCancellationById(cancellationId);
        verifyVersion(cancellation, request.version());

        SalesOrder salesOrder = cancellation.getSalesOrder();
        ensureCancellable(salesOrder);

        //建立後到送審之間，可能有其他取消已核准，所以送審時重新檢查可取消量
        List<SalesOrderCancellationLine> lines =
                salesOrderCancellationLineRepository.findAllByCancellationIdOrderByIdAsc(cancellationId);
        ensureLinesWithinCancellableQty(lines);

        cancellation.submit();
        approvalService.submit(
                ApprovalTargetType.SO_CANCELLATION,
                cancellationId,
                currentUserProvider.getCurrentUserId()
        );
        salesOrderCancellationRepository.flush();

        return toResponse(cancellation);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SO_CANCELLATION_CREATE')")
    public SalesOrderCancellationResponse withdrawCancellation(
            Long cancellationId,
            WithdrawSalesOrderCancellationRequest request
    ) {
        SalesOrderCancellation cancellation = findCancellationById(cancellationId);
        verifyVersion(cancellation, request.version());

        cancellation.withdraw();
        salesOrderCancellationRepository.flush();

        return toResponse(cancellation);
    }

    /**
     * 每筆取消明細要是這張 SO 的明細、不重複、小數位符合訂購單位，
     * 而且換算後的基準數量不超過可取消量（有效訂購量 － 已出貨量）
     */
    private List<DraftLine> validateLines(SalesOrder salesOrder, List<CancellationLineRequest> requests) {
        Map<Long, SalesOrderLine> salesOrderLines = salesOrderLineRepository
                .findAllBySalesOrderIdOrderByLineNoAsc(salesOrder.getId())
                .stream()
                .collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));
        Map<Long, BigDecimal> cancelledQtyByLine = salesOrderQuantities.findCancelledQty(salesOrderLines.keySet());
        Set<Long> seenLineIds = new HashSet<>();

        return requests.stream()
                .map(request -> {
                    SalesOrderLine line = salesOrderLines.get(request.salesOrderLineId());
                    if (line == null) {
                        throw new BusinessException(ErrorCode.SO_LINE_INVALID, "取消的明細不屬於這張 SO。");
                    }

                    String prefix = "第 " + line.getLineNo() + " 行 " + line.getCustomerItemCode() + "：";

                    if (!seenLineIds.add(line.getId())) {
                        throw new BusinessException(ErrorCode.SO_LINE_INVALID, prefix + "同一筆明細只能出現一次。");
                    }

                    int allowedScale = line.getUnit().getDecimalPlaces();
                    if (request.quantity().stripTrailingZeros().scale() > allowedScale) {
                        throw new BusinessException(
                                ErrorCode.SO_LINE_INVALID,
                                prefix + "「" + line.getUnit().getDisplayName() + "」最多 " + allowedScale + " 位小數。"
                        );
                    }

                    BigDecimal baseQty = SalesOrderAmounts.baseQty(request.quantity(), line.getConversionFactor());
                    salesOrderQuantities.ensureWithinCancellableQty(line, baseQty, cancelledQtyByLine);

                    return new DraftLine(line, request.quantity(), baseQty);
                })
                .toList();
    }

    private void ensureLinesWithinCancellableQty(List<SalesOrderCancellationLine> lines) {
        Map<Long, BigDecimal> cancelledQtyByLine = salesOrderQuantities.findCancelledQty(
                lines.stream().map(line -> line.getSalesOrderLine().getId()).toList()
        );

        for (SalesOrderCancellationLine line : lines) {
            salesOrderQuantities.ensureWithinCancellableQty(line.getSalesOrderLine(), line.getBaseQty(), cancelledQtyByLine);
        }
    }

    //只有已核准、尚未結案的 SO 才有未出貨餘量可以取消
    private void ensureCancellable(SalesOrder salesOrder) {
        if (salesOrder.getStatus() != SalesOrderStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SO_STATUS_CONFLICT);
        }
    }

    private void saveLines(SalesOrderCancellation cancellation, List<DraftLine> lines) {
        for (DraftLine line : lines) {
            salesOrderCancellationLineRepository.save(new SalesOrderCancellationLine(
                    cancellation,
                    line.salesOrderLine(),
                    line.quantity(),
                    line.baseQty()
            ));
        }
    }

    private SalesOrderCancellationResponse toResponse(SalesOrderCancellation cancellation) {
        return salesOrderCancellationMapper.toResponse(
                cancellation,
                salesOrderCancellationLineRepository.findAllByCancellationIdOrderByIdAsc(cancellation.getId())
        );
    }

    private SalesOrder findSalesOrder(Long salesOrderId) {
        return salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    private SalesOrderCancellation findCancellationById(Long cancellationId) {
        return salesOrderCancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_CANCELLATION_NOT_FOUND));
    }

    private void verifyVersion(SalesOrderCancellation cancellation, Long expectedVersion) {
        if (!Objects.equals(cancellation.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    //已通過檢查、準備寫入的取消明細
    private record DraftLine(
            SalesOrderLine salesOrderLine,
            BigDecimal quantity,
            BigDecimal baseQty
    ) {
    }
}
