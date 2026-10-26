package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryReservationEvent;
import com.smartdistribution.inventory.entity.ReservationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryReservationEventRepository extends JpaRepository<InventoryReservationEvent, Long> {

    List<InventoryReservationEvent> findAllByReservationIdInOrderByIdAsc(List<Long> reservationIds);

    //一致性檢查用：每筆預留的 RESERVE 加總減掉 CONSUME、RELEASE 加總
    @Query("""
            SELECT new com.smartdistribution.inventory.repository.QuantityByKeyProjection(
                event.reservation.id,
                SUM(
                    CASE 
                        WHEN event.eventType = :reserve THEN event.quantity 
                        ELSE -event.quantity 
                    END
                )
            )
            FROM InventoryReservationEvent event
            GROUP BY event.reservation.id
            """)
    List<QuantityByKeyProjection> sumNetQuantityByReservation(@Param("reserve") ReservationEventType reserve);
}
