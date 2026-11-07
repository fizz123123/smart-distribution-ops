package com.smartdistribution.fulfillment.service.impl;

import com.smartdistribution.document.entity.DocumentType;
import com.smartdistribution.document.service.DocumentService;
import com.smartdistribution.document.service.StoredDocument;
import com.smartdistribution.fulfillment.dto.request.CreateDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderLineRequest;
import com.smartdistribution.fulfillment.dto.request.PostDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.ReadyDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.ReadyLineRequest;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderDetailResponse;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderSummaryResponse;
import com.smartdistribution.fulfillment.entity.DeliveryOrder;
import com.smartdistribution.fulfillment.entity.DeliveryOrderLine;
import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;
import com.smartdistribution.fulfillment.mapper.DeliveryOrderAssembler;
import com.smartdistribution.fulfillment.repository.DeliveryOrderLineRepository;
import com.smartdistribution.fulfillment.repository.DeliveryOrderRepository;
import com.smartdistribution.fulfillment.service.DeliveryOrderMasterData;
import com.smartdistribution.fulfillment.service.DeliveryOrderPdfLayout;
import com.smartdistribution.fulfillment.service.DeliveryOrderService;
import com.smartdistribution.inventory.service.InventoryService.DeliveryLine;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderDeliveryService.SalesOrderForDelivery;
import com.smartdistribution.sales.service.SalesOrderDeliveryService;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import com.smartdistribution.sales.service.ShipmentQuantityProvider;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.request.VersionRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryOrderLineRepository deliveryOrderLineRepository;
    private final ShipmentQuantityProvider shipmentQuantityProvider;
    private final DeliveryOrderMasterData deliveryOrderMasterData;
    private final DeliveryOrderPdfLayout deliveryOrderPdfLayout;
    private final DeliveryOrderAssembler deliveryOrderAssembler;
    private final SalesOrderDeliveryService salesOrderDeliveryService;
    private final InventoryService inventoryService;
    private final DocumentService documentService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("hasAuthority('DO_VIEW')")
    public PageResponse<DeliveryOrderSummaryResponse> findDeliveryOrders(
            String keyword,
            DeliveryOrderStatus status,
            Long salesOrderId,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        return PageResponse.from(deliveryOrderAssembler.toSummaries(
                deliveryOrderRepository.search(normalizedKeyword, status, salesOrderId, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('DO_VIEW')")
    public DeliveryOrderDetailResponse findDeliveryOrder(Long deliveryOrderId) {
        return deliveryOrderAssembler.toDetail(findDeliveryOrderById(deliveryOrderId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DO_CREATE')")
    public DeliveryOrderDetailResponse createDeliveryOrder(Long salesOrderId, CreateDeliveryOrderRequest request) {
        SalesOrderForDelivery salesOrder = findSalesOrder(salesOrderId);
        if (!salesOrder.approved()) {
            throw new BusinessException(ErrorCode.DO_SALES_ORDER_NOT_APPROVED);
        }

        List<CheckedLine> lines = checkLines(salesOrder, request.lines());

        DeliveryOrder deliveryOrder = new DeliveryOrder(
                salesOrderId,
                request.plannedShipmentDate(),
                request.remark(),
                currentUserProvider.getCurrentUserId()
        );

        //DO 編號依 ID 產生，所以要先 insert 取得 id 再回填
        deliveryOrderRepository.saveAndFlush(deliveryOrder);
        deliveryOrder.assignNumber();

        for (int index = 0; index < lines.size(); index++) {
            CheckedLine line = lines.get(index);
            deliveryOrderLineRepository.save(new DeliveryOrderLine(
                    deliveryOrder,
                    index + 1,
                    line.salesOrderLine().id(),
                    line.salesOrderLine().companyItemId(),
                    line.salesOrderLine().unit(),
                    line.salesOrderLine().conversionFactor(),
                    line.plannedQuantity()
            ));
        }
        deliveryOrderRepository.flush();

        return deliveryOrderAssembler.toDetail(deliveryOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DO_CREATE')")
    public DeliveryOrderDetailResponse withdrawDeliveryOrder(Long deliveryOrderId, VersionRequest request) {
        DeliveryOrder deliveryOrder = findDeliveryOrderById(deliveryOrderId);
        verifyVersion(deliveryOrder, request.version());

        Long actorId = currentUserProvider.getCurrentUserId();
        if (!deliveryOrder.isCreatedBy(actorId)) {
            throw new BusinessException(ErrorCode.DO_WITHDRAW_FORBIDDEN);
        }

        deliveryOrder.withdraw(actorId, Instant.now());
        deliveryOrderRepository.flush();

        return deliveryOrderAssembler.toDetail(deliveryOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DO_VOID')")
    public DeliveryOrderDetailResponse voidDeliveryOrder(Long deliveryOrderId, ReasonRequest request) {
        DeliveryOrder deliveryOrder = findDeliveryOrderById(deliveryOrderId);
        verifyVersion(deliveryOrder, request.version());

        Long actorId = currentUserProvider.getCurrentUserId();

        deliveryOrder.voidOrder(request.reason(), actorId, Instant.now());
        //完成揀貨過就有出貨單，要一起作廢
        if (deliveryOrder.getReadyAt() != null) {
            documentService.voidDocument(DocumentType.DELIVERY_ORDER, deliveryOrderId, actorId, deliveryOrder.getVoidReason());
        }
        deliveryOrderRepository.flush();

        return deliveryOrderAssembler.toDetail(deliveryOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DO_READY')")
    public DeliveryOrderDetailResponse readyDeliveryOrder(Long deliveryOrderId, ReadyDeliveryOrderRequest request) {
        DeliveryOrder deliveryOrder = findDeliveryOrderById(deliveryOrderId);
        verifyVersion(deliveryOrder, request.version());

        //先擋狀態，已過帳或作廢的 DO 不必再檢查數量
        if (!deliveryOrder.isDraft()) {
            throw new BusinessException(ErrorCode.DO_STATUS_CONFLICT);
        }

        Long actorId = currentUserProvider.getCurrentUserId();
        List<DeliveryOrderLine> lines = deliveryOrderLineRepository.findAllByDeliveryOrderIdOrderByLineNoAsc(deliveryOrderId);
        recordActualQuantities(lines, request.lines());

        deliveryOrder.markReady(actorId, Instant.now());

        //出貨單依實際量產生，交給司機與客戶的就是這一份
        SalesOrderForDelivery salesOrder = findSalesOrder(deliveryOrder.getSalesOrderId());
        Customer customer = deliveryOrderMasterData.findCustomers(List.of(salesOrder.customerId()))
                .get(salesOrder.customerId());
        Map<Long, CompanyItem> items = deliveryOrderMasterData.findCompanyItems(
                lines.stream().map(DeliveryOrderLine::getCompanyItemId).collect(Collectors.toSet())
        );
        byte[] pdf = deliveryOrderPdfLayout.render(deliveryOrder, salesOrder, customer, lines, items);
        documentService.store(
                DocumentType.DELIVERY_ORDER,
                deliveryOrderId,
                deliveryOrder.getDoNumber(),
                pdf,
                actorId
        );
        deliveryOrderRepository.flush();

        return deliveryOrderAssembler.toDetail(deliveryOrder);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('DO_POST')")
    public DeliveryOrderDetailResponse postDeliveryOrder(Long deliveryOrderId, PostDeliveryOrderRequest request) {
        //先鎖 DO 表頭，再由庫存依料號順序鎖餘額；同一張 DO 同時送兩次，第二次會在這裡等，之後因狀態不符被擋下
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByIdForUpdate(deliveryOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_ORDER_NOT_FOUND));
        verifyVersion(deliveryOrder, request.version());

        if (!deliveryOrder.isReady()) {
            throw new BusinessException(ErrorCode.DO_STATUS_CONFLICT);
        }
        if (request.actualShipmentDate().isAfter(LocalDate.now(TAIPEI))) {
            throw new BusinessException(ErrorCode.DO_SHIPMENT_DATE_INVALID);
        }

        Long actorId = currentUserProvider.getCurrentUserId();
        List<DeliveryLine> shippedLines = deliveryOrderLineRepository
                .findAllByDeliveryOrderIdOrderByLineNoAsc(deliveryOrderId)
                .stream()
                .filter(DeliveryOrderLine::isShipped)
                .map(line -> new DeliveryLine(
                        line.getId(),
                        line.getCompanyItemId(),
                        line.getSalesOrderLineId(),
                        line.getActualBaseQty()
                ))
                .toList();
        inventoryService.consumeForDelivery(shippedLines, actorId);

        deliveryOrder.post(request.actualShipmentDate(), actorId, Instant.now());
        //SO 結案要算已出貨量，所以先把 DO 的新狀態寫進資料庫
        deliveryOrderRepository.flush();
        salesOrderDeliveryService.closeIfFulfilled(deliveryOrder.getSalesOrderId());

        return deliveryOrderAssembler.toDetail(deliveryOrder);
    }

    @Override
    @PreAuthorize("hasAuthority('DO_DOCUMENT_VIEW')")
    public StoredDocument downloadDocument(Long deliveryOrderId) {
        DeliveryOrder deliveryOrder = findDeliveryOrderById(deliveryOrderId);
        StoredDocument document = documentService.load(DocumentType.DELIVERY_ORDER, deliveryOrderId);

        //下載紀錄寫在應用程式 log；traceId 由 log 設定帶出
        log.info(
                "下載正式文件：使用者 {}，{}，檔名 {}",
                currentUserProvider.getCurrentUserId(),
                deliveryOrder.getDoNumber(),
                document.fileName()
        );

        return document;
    }

    /**
     * 完成揀貨的數量檢查：DO 上每一筆都要回報一次；0 ≤ 實際量 ≤ 預計量、小數位符合單位；至少一筆大於 0
     */
    private void recordActualQuantities(List<DeliveryOrderLine> lines, List<ReadyLineRequest> requests) {
        Map<Long, DeliveryOrderLine> linesById = lines.stream()
                .collect(Collectors.toMap(DeliveryOrderLine::getId, Function.identity()));
        Set<Long> reportedLineIds = new HashSet<>();

        for (ReadyLineRequest request : requests) {
            DeliveryOrderLine line = linesById.get(request.deliveryOrderLineId());
            if (line == null || !reportedLineIds.add(line.getId())) {
                throw new BusinessException(
                        ErrorCode.DO_ACTUAL_QUANTITY_INVALID,
                        "回報的明細不屬於這張 DO，或同一筆回報了兩次。"
                );
            }

            String prefix = "第 " + line.getLineNo() + " 行：";

            int allowedScale = line.getUnit().getDecimalPlaces();
            if (request.actualQuantity().stripTrailingZeros().scale() > allowedScale) {
                throw new BusinessException(
                        ErrorCode.DO_ACTUAL_QUANTITY_INVALID,
                        prefix + "「" + line.getUnit().getDisplayName() + "」最多 " + allowedScale + " 位小數。"
                );
            }

            if (request.actualQuantity().compareTo(line.getPlannedQuantity()) > 0) {
                throw new BusinessException(
                        ErrorCode.DO_ACTUAL_QUANTITY_INVALID,
                        prefix + "實際量不能超過通知的 "
                                + line.getPlannedQuantity().stripTrailingZeros().toPlainString() + "。"
                );
            }

            line.recordActual(request.actualQuantity());
        }

        if (reportedLineIds.size() != lines.size()) {
            throw new BusinessException(ErrorCode.DO_ACTUAL_QUANTITY_INVALID, "DO 上的每一筆都要回報實際量，沒有找到就填 0。");
        }

        if (lines.stream().noneMatch(DeliveryOrderLine::isShipped)) {
            throw new BusinessException(ErrorCode.DO_ACTUAL_QUANTITY_INVALID, "至少要有一筆出貨；都不出請作廢這張 DO。");
        }
    }

    private SalesOrderForDelivery findSalesOrder(Long salesOrderId) {
        SalesOrderForDelivery salesOrder = salesOrderDeliveryService.findForDelivery(List.of(salesOrderId))
                .get(salesOrderId);
        if (salesOrder == null) {
            throw new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND);
        }
        return salesOrder;
    }

    /**
     * 通知出貨的明細檢查，錯誤訊息帶 SO 的行號：
     * 1. 每筆都是這張 SO 的明細，而且只出現一次
     * 2. 小數位符合 SO 明細的單位
     * 3. 換成基準單位後，不超過「有效預留 － 已排入其他 DO 的數量」
     */
    private List<CheckedLine> checkLines(SalesOrderForDelivery salesOrder, List<DeliveryOrderLineRequest> requests) {
        Map<Long, SalesOrderForDelivery.Line> salesOrderLines = salesOrder.lines()
                .stream()
                .collect(Collectors.toMap(SalesOrderForDelivery.Line::id, Function.identity()));

        List<CheckedLine> checkedLines = new ArrayList<>();
        Set<Long> seenLineIds = new HashSet<>();

        for (DeliveryOrderLineRequest request : requests) {
            SalesOrderForDelivery.Line line = salesOrderLines.get(request.salesOrderLineId());
            if (line == null) {
                throw new BusinessException(ErrorCode.DO_LINE_INVALID, "出貨的明細不屬於這張 SO。");
            }

            String prefix = linePrefix(line);

            if (!seenLineIds.add(line.id())) {
                throw new BusinessException(ErrorCode.DO_LINE_INVALID, prefix + "同一筆明細只能出現一次。");
            }

            int allowedScale = line.unit().getDecimalPlaces();
            if (request.plannedQuantity().stripTrailingZeros().scale() > allowedScale) {
                throw new BusinessException(
                        ErrorCode.DO_LINE_INVALID,
                        prefix + "「" + line.unit().getDisplayName() + "」最多 " + allowedScale + " 位小數。"
                );
            }

            checkedLines.add(new CheckedLine(
                    line,
                    request.plannedQuantity(),
                    SalesOrderAmounts.baseQty(request.plannedQuantity(), line.conversionFactor())
            ));
        }

        checkAgainstReservation(checkedLines);

        return checkedLines;
    }

    //可通知出貨量 ＝ 有效預留 － 已排入其他 DO 的數量；排在 DO 裡的仍在預留裡，所以要扣掉
    private void checkAgainstReservation(List<CheckedLine> checkedLines) {
        List<Long> salesOrderLineIds = checkedLines.stream().map(line -> line.salesOrderLine().id()).toList();
        Map<Long, BigDecimal> reservedByLine = inventoryService.findOpenQtyBySalesOrderLines(salesOrderLineIds);
        Map<Long, ShipmentQuantity> shipmentsByLine = shipmentQuantityProvider.findBySalesOrderLines(salesOrderLineIds);

        for (CheckedLine checkedLine : checkedLines) {
            Long salesOrderLineId = checkedLine.salesOrderLine().id();
            BigDecimal availableQty = reservedByLine.getOrDefault(salesOrderLineId, BigDecimal.ZERO)
                    .subtract(shipmentsByLine.getOrDefault(salesOrderLineId, ShipmentQuantity.NONE).deliveringQty())
                    .max(BigDecimal.ZERO);

            if (checkedLine.plannedBaseQty().compareTo(availableQty) > 0) {
                throw new BusinessException(
                        ErrorCode.DO_QUANTITY_EXCEEDS_RESERVATION,
                        linePrefix(checkedLine.salesOrderLine()) + "最多還能通知 "
                                + availableQty.stripTrailingZeros().toPlainString() + "（庫存基準單位）。"
                );
            }
        }
    }

    private static String linePrefix(SalesOrderForDelivery.Line line) {
        return "第 " + line.lineNo() + " 行 " + line.customerItemCode() + "：";
    }

    private DeliveryOrder findDeliveryOrderById(Long deliveryOrderId) {
        return deliveryOrderRepository.findById(deliveryOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_ORDER_NOT_FOUND));
    }

    private void verifyVersion(DeliveryOrder deliveryOrder, Long expectedVersion) {
        if (!Objects.equals(deliveryOrder.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * 檢查通過的一筆通知
     */
    private record CheckedLine(
            SalesOrderForDelivery.Line salesOrderLine,
            BigDecimal plannedQuantity,
            BigDecimal plannedBaseQty
    ) {
    }
}
