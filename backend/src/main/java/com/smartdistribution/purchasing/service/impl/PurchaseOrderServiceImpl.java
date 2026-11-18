package com.smartdistribution.purchasing.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.document.entity.DocumentType;
import com.smartdistribution.document.service.DocumentService;
import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.purchasing.dto.request.CreateBatchDraftsRequest;
import com.smartdistribution.purchasing.dto.request.CreatePurchaseOrderRequest;
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
import com.smartdistribution.purchasing.service.PurchaseOrderLineValidator.CheckedLine;
import com.smartdistribution.purchasing.service.PurchaseOrderLineValidator.LineInput;
import com.smartdistribution.purchasing.service.PurchaseOrderLineValidator;
import com.smartdistribution.purchasing.service.PurchaseOrderMasterData;
import com.smartdistribution.purchasing.service.PurchaseOrderQuantities;
import com.smartdistribution.purchasing.service.PurchaseOrderService;
import com.smartdistribution.sales.service.SalesOrderDemandService;
import com.smartdistribution.sales.service.SalesOrderDemandService.SalesOrderLineDemand;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final SalesOrderDemandService salesOrderDemandService;
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
        return new LastPriceResponse(findLastApprovedPrice(supplierId, companyItemId, unit));
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
    @PreAuthorize("hasAuthority('PO_CREATE')")
    public List<PurchaseOrderDetailResponse> createBatchDrafts(CreateBatchDraftsRequest request) {
        //1. 補上料號、基準單位與供應商；行號用整批的順序，錯誤訊息才對得上畫面
        List<BatchLine> batchLines = toBatchLines(request.lines());
        Map<Integer, Long> supplierByLineNo = batchLines.stream()
                .collect(Collectors.toMap(batchLine -> batchLine.input().lineNo(), BatchLine::supplierId));

        //2. 整批一起檢查，同一筆 SO 明細重複、超過尚待規劃都在這裡擋下
        List<CheckedLine> checkedLines = purchaseOrderLineValidator.validate(
                batchLines.stream().map(BatchLine::input).toList(),
                PurchaseOrderQuantities.NO_EXCLUDED_PURCHASE_ORDER
        );

        //3. 依供應商分組，保留第一次出現的順序
        Map<Long, List<CheckedLine>> linesBySupplier = new LinkedHashMap<>();
        for (CheckedLine checkedLine : checkedLines) {
            Long supplierId = supplierByLineNo.get(checkedLine.input().lineNo());
            linesBySupplier.computeIfAbsent(supplierId, id -> new ArrayList<>()).add(checkedLine);
        }

        //4. 一家供應商一張草稿
        return linesBySupplier.entrySet().stream()
                .map(entry -> createBatchDraft(entry.getKey(), entry.getValue()))
                .toList();
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
    public PurchaseOrderDetailResponse submitPurchaseOrder(Long purchaseOrderId, VersionRequest request) {
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
    public PurchaseOrderDetailResponse cancelDraft(Long purchaseOrderId, VersionRequest request) {
        PurchaseOrder purchaseOrder = findPurchaseOrderById(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        purchaseOrder.cancelDraft();
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_CLOSE')")
    public PurchaseOrderDetailResponse cancelApproved(Long purchaseOrderId, ReasonRequest request) {
        //和收貨一樣先鎖 PO 表頭，避免取消的同時有人在收貨
        PurchaseOrder purchaseOrder = findPurchaseOrderForUpdate(purchaseOrderId);
        verifyVersion(purchaseOrder, request.version());

        Long actorId = currentUserProvider.getCurrentUserId();
        purchaseOrder.cancelApproved(actorId, request.reason(), Instant.now());
        purchaseOrderLineRepository.findAllByPurchaseOrderIdOrderByLineNoAsc(purchaseOrderId)
                .forEach(PurchaseOrderLine::cancel);

        documentService.voidDocument(DocumentType.PURCHASE_ORDER, purchaseOrderId, actorId, purchaseOrder.getCancelReason());
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PO_CLOSE')")
    public PurchaseOrderDetailResponse closeShort(Long purchaseOrderId, ReasonRequest request) {
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

    //批次的一行：檢查用的明細輸入，加上這一行要向哪家供應商買
    private record BatchLine(LineInput input, Long supplierId) {
    }

    //由 SO 明細找出料號，帶入基準單位；供應商沒指定就用偏好供應商，且必須啟用
    private List<BatchLine> toBatchLines(List<CreateBatchDraftsRequest.Line> requestLines) {
        Map<Long, SalesOrderLineDemand> demands = salesOrderDemandService.findDemands(
                requestLines.stream().map(CreateBatchDraftsRequest.Line::salesOrderLineId).toList()
        );

        List<BatchLine> batchLines = new ArrayList<>();
        for (int index = 0; index < requestLines.size(); index++) {
            CreateBatchDraftsRequest.Line line = requestLines.get(index);
            String prefix = "第 " + (index + 1) + " 行：";

            SalesOrderLineDemand demand = demands.get(line.salesOrderLineId());
            if (demand == null) {
                throw new BusinessException(ErrorCode.PO_SOURCE_INVALID, prefix + "找不到來源 SO 明細。");
            }

            CompanyItem item = purchaseOrderMasterData.findCompanyItem(demand.companyItemId());
            Long supplierId = line.supplierId() != null ? line.supplierId() : item.getPreferredSupplierId();
            if (supplierId == null) {
                throw new BusinessException(
                        ErrorCode.PO_BATCH_SUPPLIER_REQUIRED,
                        prefix + item.getItemCode() + " 沒有偏好供應商，請指定供應商。"
                );
            }

            Supplier supplier = purchaseOrderMasterData.findSupplier(supplierId);
            if (supplier.getStatus() != MasterDataStatus.ACTIVE) {
                throw new BusinessException(
                        ErrorCode.PO_SUPPLIER_INACTIVE,
                        prefix + "供應商 " + supplier.getSupplierCode() + " 尚未啟用或已停用。"
                );
            }

            batchLines.add(new BatchLine(
                    new LineInput(
                            index + 1,
                            item.getId(),
                            line.salesOrderLineId(),
                            line.quantity(),
                            item.getBaseUnit(),
                            null
                    ),
                    supplierId
            ));
        }

        return batchLines;
    }

    //一家供應商一張草稿：行號從 1 開始，單價預填最近一次核准的單價，到貨日留空
    private PurchaseOrderDetailResponse createBatchDraft(Long supplierId, List<CheckedLine> checkedLines) {
        PurchaseOrder purchaseOrder = new PurchaseOrder(supplierId, currentUserProvider.getCurrentUserId(), null, null);
        purchaseOrderRepository.saveAndFlush(purchaseOrder);
        purchaseOrder.assignNumber();

        List<CheckedLine> numberedLines = new ArrayList<>();
        for (int index = 0; index < checkedLines.size(); index++) {
            LineInput input = checkedLines.get(index).input();
            numberedLines.add(new CheckedLine(
                    new LineInput(
                            index + 1,
                            input.companyItemId(),
                            input.salesOrderLineId(),
                            input.quantity(),
                            input.unit(),
                            findLastApprovedPrice(supplierId, input.companyItemId(), input.unit())
                    ),
                    checkedLines.get(index).conversionFactor()
            ));
        }

        saveLines(purchaseOrder, numberedLines);
        purchaseOrderRepository.flush();

        return purchaseOrderAssembler.toDetail(purchaseOrder);
    }

    //同一供應商、料號與單位最近一次核准的單價；從未採購過時為 null
    private BigDecimal findLastApprovedPrice(Long supplierId, Long companyItemId, UnitOfMeasure unit) {
        List<BigDecimal> prices = purchaseOrderLineRepository.findApprovedPrices(
                supplierId, companyItemId, unit, PageRequest.of(0, 1)
        );

        return prices.isEmpty() ? null : prices.getFirst();
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
