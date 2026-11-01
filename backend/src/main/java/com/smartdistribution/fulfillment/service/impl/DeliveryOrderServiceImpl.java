package com.smartdistribution.fulfillment.service.impl;

import com.smartdistribution.fulfillment.dto.request.CreateDeliveryOrderRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderLineRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderReasonRequest;
import com.smartdistribution.fulfillment.dto.request.DeliveryOrderVersionRequest;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderDetailResponse;
import com.smartdistribution.fulfillment.dto.response.DeliveryOrderSummaryResponse;
import com.smartdistribution.fulfillment.entity.DeliveryOrder;
import com.smartdistribution.fulfillment.entity.DeliveryOrderLine;
import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;
import com.smartdistribution.fulfillment.mapper.DeliveryOrderAssembler;
import com.smartdistribution.fulfillment.repository.DeliveryOrderLineRepository;
import com.smartdistribution.fulfillment.repository.DeliveryOrderRepository;
import com.smartdistribution.fulfillment.service.DeliveryOrderQuantities;
import com.smartdistribution.fulfillment.service.DeliveryOrderService;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderDeliveryService;
import com.smartdistribution.sales.service.SalesOrderForDelivery;
import com.smartdistribution.sales.service.ShipmentQuantity;
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
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryOrderLineRepository deliveryOrderLineRepository;
    private final DeliveryOrderQuantities deliveryOrderQuantities;
    private final DeliveryOrderAssembler deliveryOrderAssembler;
    private final SalesOrderDeliveryService salesOrderDeliveryService;
    private final InventoryService inventoryService;
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
        SalesOrderForDelivery salesOrder = salesOrderDeliveryService.findForDelivery(List.of(salesOrderId))
                .get(salesOrderId);
        if (salesOrder == null) {
            throw new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND);
        }
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
    public DeliveryOrderDetailResponse withdrawDeliveryOrder(Long deliveryOrderId, DeliveryOrderVersionRequest request) {
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
    public DeliveryOrderDetailResponse voidDeliveryOrder(Long deliveryOrderId, DeliveryOrderReasonRequest request) {
        DeliveryOrder deliveryOrder = findDeliveryOrderById(deliveryOrderId);
        verifyVersion(deliveryOrder, request.version());

        deliveryOrder.voidOrder(request.reason(), currentUserProvider.getCurrentUserId(), Instant.now());
        deliveryOrderRepository.flush();

        return deliveryOrderAssembler.toDetail(deliveryOrder);
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
        Map<Long, ShipmentQuantity> shipmentsByLine = deliveryOrderQuantities.findBySalesOrderLines(salesOrderLineIds);

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
