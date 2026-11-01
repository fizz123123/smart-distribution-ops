package com.smartdistribution.purchasing.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.document.entity.DocumentType;
import com.smartdistribution.document.service.DocumentService;
import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.purchasing.dto.request.CreatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.request.PurchaseOrderReasonRequest;
import com.smartdistribution.purchasing.dto.request.PurchaseOrderVersionRequest;
import com.smartdistribution.purchasing.dto.request.UpdatePurchaseOrderRequest;
import com.smartdistribution.purchasing.dto.response.LastPriceResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderDetailResponse;
import com.smartdistribution.purchasing.dto.response.PurchaseOrderSummaryResponse;
import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import com.smartdistribution.purchasing.mapper.PurchaseOrderAssembler;
import com.smartdistribution.purchasing.repository.PurchaseOrderLineRepository;
import com.smartdistribution.purchasing.repository.PurchaseOrderRepository;
import com.smartdistribution.purchasing.service.PurchaseOrderAmounts;
import com.smartdistribution.purchasing.service.PurchaseOrderLineValidator;
import com.smartdistribution.purchasing.service.PurchaseOrderLineValidator.CheckedLine;
import com.smartdistribution.purchasing.service.PurchaseOrderLineValidator.LineInput;
import com.smartdistribution.purchasing.service.PurchaseOrderMasterData;
import com.smartdistribution.purchasing.service.PurchaseOrderQuantities;
import com.smartdistribution.purchasing.service.PurchaseOrderService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderMasterData purchaseOrderMasterData;
    private final PurchaseOrderLineValidator purchaseOrderLineValidator;
    private final PurchaseOrderAssembler purchaseOrderAssembler;
    private final ApprovalService approvalService;
    private final DocumentService documentService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('PO_VIEW')")
    public PageResponse<PurchaseOrderSummaryResponse> findPurchaseOrders(
            String keyword,
            PurchaseOrderStatus status,
            Long supplierId,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        return PageResponse.from(purchaseOrderAssembler.toSummaries(
                purchaseOrderRepository.search(normalizedKeyword, status, supplierId, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('PO_VIEW')")
    public PurchaseOrderDetailResponse findPurchaseOrder(Long purchaseOrderId) {
        return purchaseOrderAssembler.toDetail(findPurchaseOrderById(purchaseOrderId));
    }

    @Override
    @PreAuthorize("hasAuthority('PO_CREATE')")
    public LastPriceResponse findLastPrice(Long supplierId, Long companyItemId, UnitOfMeasure unit) {
        List<BigDecimal> prices = purchaseOrderLineRepository.findApprovedPrices(
                supplierId, companyItemId, unit, PageRequest.of(0, 1)
        );

        return new LastPriceResponse(prices.isEmpty() ? null : prices.getFirst());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_CREATE')")
    public PurchaseOrderDetailResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
        Supplier supplier = findActiveSupplier(request.supplierId());
        List<CheckedLine> lines = purchaseOrderLineValidator.validate(
                LineInput.fromRequests(request.lines()),
                PurchaseOrderQuantities.NO_EXCLUDED_PURCHASE_ORDER
        );

        PurchaseOrder purchaseOrder = new PurchaseOrder(
                supplier.getId(),
                currentUserProvider.getCurrentUserId(),
                request.expectedArrivalDate(),
                request.remark()
        );

        //PO 編號依 ID 產生，所以要先 insert 取得 id 再回填
        purchaseOrderRepository.saveAndFlush(purchaseOrder);
        purchaseOrder.assignNumber();
        saveLines(purchaseOrder, lines);
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_UPDATE')")
    public PurchaseOrderDetailResponse updatePurchaseOrder(Long purchaseOrderId, UpdatePurchaseOrderRequest request) {
        PurchaseOrder purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        if (!purchaseOrder.isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.PO_STATUS_CONFLICT);
        }

        Supplier supplier = findActiveSupplier(request.supplierId());
        List<CheckedLine> lines = purchaseOrderLineValidator.validate(
                LineInput.fromRequests(request.lines()),
                purchaseOrderId
        );

        //明細整批取代：先刪除舊明細再寫入，行號才不會和舊資料的唯一約束衝突
        purchaseOrderLineRepository.deleteAllByPurchaseOrderId(purchaseOrderId);
        saveLines(purchaseOrder, lines);
        purchaseOrder.updateDraft(supplier.getId(), request.expectedArrivalDate(), request.remark(), Instant.now());
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_SUBMIT')")
    public PurchaseOrderDetailResponse submitPurchaseOrder(Long purchaseOrderId, PurchaseOrderVersionRequest request) {
        PurchaseOrder purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        if (!purchaseOrder.isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.PO_STATUS_CONFLICT);
        }

        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrderId);
        boolean incomplete = purchaseOrder.getExpectedArrivalDate() == null
                || lines.isEmpty()
                || lines.stream().anyMatch(line -> line.getUnitPriceExcludingTax() == null);
        if (incomplete) {
            throw new BusinessException(ErrorCode.PO_SUBMIT_INFO_INCOMPLETE);
        }

        findActiveSupplier(purchaseOrder.getSupplierId());

        //重跑草稿的所有檢查，並用最新的換算倍率重算基準數量與小計
        Map<Integer, BigDecimal> factorByLineNo = purchaseOrderLineValidator
                .validate(lines.stream().map(LineInput::from).toList(), purchaseOrderId)
                .stream()
                .collect(Collectors.toMap(checked -> checked.input().lineNo(), CheckedLine::conversionFactor));
        lines.forEach(line -> line.applyConversion(factorByLineNo.get(line.getLineNo())));

        purchaseOrder.submit(
                PurchaseOrderAmounts.untaxedTotal(
                        lines.stream().map(PurchaseOrderLine::getLineAmountExcludingTax).toList()
                ),
                Instant.now()
        );
        approvalService.submit(
                ApprovalTargetType.PURCHASE_ORDER,
                purchaseOrderId,
                currentUserProvider.getCurrentUserId()
        );
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_UPDATE')")
    public PurchaseOrderDetailResponse cancelDraft(Long purchaseOrderId, PurchaseOrderVersionRequest request) {
        PurchaseOrder purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        purchaseOrder.cancelDraft();
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_CLOSE')")
    public PurchaseOrderDetailResponse cancelApproved(Long purchaseOrderId, PurchaseOrderReasonRequest request) {
        //和收貨一樣先鎖 PO 表頭，避免取消的同時有人在收貨
        PurchaseOrder purchaseOrder = findPurchaseOrderForUpdate(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        Long actorId = currentUserProvider.getCurrentUserId();
        purchaseOrder.cancelApproved(actorId, request.reason(), Instant.now());
        purchaseOrderLineRepository.findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrderId)
                .forEach(PurchaseOrderLine::cancel);

        documentService.voidDocument(DocumentType.PURCHASE_ORDER, purchaseOrderId, actorId, request.reason().strip());
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_CLOSE')")
    public PurchaseOrderDetailResponse closeShort(Long purchaseOrderId, PurchaseOrderReasonRequest request) {
        PurchaseOrder purchaseOrder = findPurchaseOrderForUpdate(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        purchaseOrder.closeShort(currentUserProvider.getCurrentUserId(), request.reason(), Instant.now());
        purchaseOrderLineRepository.findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrderId)
                .forEach(PurchaseOrderLine::closeShort);
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @PreAuthorize("hasAuthority('PO_DOCUMENT_VIEW')")
    public StoredDocument downloadDocument(Long purchaseOrderId) {
        PurchaseOrder purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        StoredDocument document = documentService.load(DocumentType.PURCHASE_ORDER, purchaseOrderId);

        //下載紀錄寫在應用程式 log；traceId 由 log 設定帶出
        log.info(
                "下載正式文件：使用者 {}，{}，檔名 {}",
                currentUserProvider.getCurrentUserId(),
                purchaseOrder.getPoNumber(),
                document.fileName()
        );

        return document;
    }

    private void saveLines(PurchaseOrder purchaseOrder, List<CheckedLine> lines) {
        for (CheckedLine line : lines) {
            LineInput input = line.input();
            purchaseOrderLineRepository.save(new PurchaseOrderLine(
                    purchaseOrder,
                    input.lineNo(),
                    input.companyItemId(),
                    input.salesOrderLineId(),
                    input.quantity(),
                    input.unit(),
                    line.conversionFactor(),
                    input.unitPriceExcludingTax()
            ));
        }
    }

    private Supplier findActiveSupplier(Long supplierId) {
        Supplier supplier = purchaseOrderMasterData.findSupplier(supplierId);

        if (supplier.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PO_SUPPLIER_INACTIVE);
        }

        return supplier;
    }

    private PurchaseOrder findPurchaseOrderById(Long purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }

    private PurchaseOrder findPurchaseOrderForUpdate(Long purchaseOrderId) {
        return purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }

    private void verifyVersion(PurchaseOrder purchaseOrder, Long expectedVersion) {
        if (!Objects.equals(purchaseOrder.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }
}
