package com.smartdistribution.inventory.dto.response;

import com.smartdistribution.inventory.entity.ReservationEventType;
import com.smartdistribution.inventory.entity.ReservationSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryReservationResponse(
        Long id,
        Long salesOrderId,
        String salesOrderNumber,
        Long salesOrderLineId,
        BigDecimal openQty,
        List<Event> events
) {

    public record Event(
            Long id,
            ReservationEventType eventType,
            BigDecimal quantity,
            ReservationSourceType sourceType,
            Long sourceId,
            Long actorId,
            Instant occurredAt
    ) {
    }
}
