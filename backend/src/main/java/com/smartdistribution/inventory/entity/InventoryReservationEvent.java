package com.smartdistribution.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 預留事件，只新增、不修改；數量一律大於 0，方向由事件類型決定
 */
@Getter
@Entity
@Table(name = "inventory_reservation_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private ReservationEventType eventType;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ReservationSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public InventoryReservationEvent(
            InventoryReservation reservation,
            ReservationEventType eventType,
            BigDecimal quantity,
            ReservationSourceType sourceType,
            Long sourceId,
            Long actorId,
            Instant occurredAt
    ) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("預留事件數量必須大於 0");
        }

        this.reservation = reservation;
        this.eventType = eventType;
        this.quantity = quantity;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }
}
