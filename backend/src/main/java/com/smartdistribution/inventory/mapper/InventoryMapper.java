package com.smartdistribution.inventory.mapper;

import com.smartdistribution.inventory.dto.response.InventoryAdjustmentResponse;
import com.smartdistribution.inventory.dto.response.InventoryBalanceResponse;
import com.smartdistribution.inventory.dto.response.InventoryReservationResponse;
import com.smartdistribution.inventory.dto.response.InventoryTransactionResponse;
import com.smartdistribution.inventory.entity.InventoryAdjustment;
import com.smartdistribution.inventory.entity.InventoryAdjustmentLine;
import com.smartdistribution.inventory.entity.InventoryReservation;
import com.smartdistribution.inventory.entity.InventoryReservationEvent;
import com.smartdistribution.inventory.entity.InventoryTransaction;
import com.smartdistribution.inventory.repository.InventoryBalanceProjection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 庫存模組的 entity 與查詢結果轉成 API 回應；只做欄位轉換，不查詢資料庫
 * 需要的關聯資料（例如調整明細、預留事件）由 service 先查好再傳入
 */
@Component
public class InventoryMapper {

    public InventoryAdjustmentResponse toAdjustmentResponse(
            InventoryAdjustment adjustment,
            List<InventoryAdjustmentLine> lines
    ) {
        return new InventoryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNumber(),
                adjustment.getReasonType(),
                adjustment.getDescription(),
                adjustment.getStatus(),
                adjustment.getReversalOfId(),
                adjustment.getPostedBy(),
                adjustment.getPostedAt(),
                adjustment.getVersion(),
                lines.stream()
                        .map(line -> new InventoryAdjustmentResponse.Line(
                                line.getCompanyItemId(),
                                line.getQuantityChangeBase()
                        ))
                        .toList()
        );
    }

    public InventoryTransactionResponse toTransactionResponse(InventoryTransaction transaction) {
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getQuantityChange(),
                transaction.getSourceId(),
                transaction.getActorId(),
                transaction.getOccurredAt()
        );
    }

    public InventoryReservationResponse toReservationResponse(
            InventoryReservation reservation,
            List<InventoryReservationEvent> events
    ) {
        return new InventoryReservationResponse(
                reservation.getId(),
                reservation.getSalesOrderId(),
                reservation.getSalesOrderNumber(),
                reservation.getSalesOrderLineId(),
                reservation.getOpenQty(),
                events.stream().map(this::toEvent).toList()
        );
    }

    // 可用庫存 ＝ 可出貨實體數量 － 有效預留總量
    public InventoryBalanceResponse toBalanceResponse(InventoryBalanceProjection row, BigDecimal reservedQty) {
        return new InventoryBalanceResponse(
                row.companyItemId(),
                row.itemCode(),
                row.name(),
                row.baseUnit(),
                row.onHandQty(),
                reservedQty,
                row.onHandQty().subtract(reservedQty),
                row.safetyStockBaseQty()
        );
    }

    private InventoryReservationResponse.Event toEvent(InventoryReservationEvent event) {
        return new InventoryReservationResponse.Event(
                event.getId(),
                event.getEventType(),
                event.getQuantity(),
                event.getSourceType(),
                event.getSourceId(),
                event.getActorId(),
                event.getOccurredAt()
        );
    }
}
