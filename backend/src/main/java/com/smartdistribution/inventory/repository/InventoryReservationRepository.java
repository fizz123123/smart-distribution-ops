package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    boolean existsByCompanyItemId(Long companyItemId);

    List<InventoryReservation> findAllByCompanyItemIdOrderByIdAsc(Long companyItemId);

    List<InventoryReservation> findAllBySalesOrderLineIdIn(Collection<Long> salesOrderLineIds);

    @Query("""
            SELECT new com.smartdistribution.inventory.repository.QuantityByKeyProjection(
                reservation.companyItemId, 
                SUM(reservation.openQty)
            )
            FROM InventoryReservation reservation
            GROUP BY reservation.companyItemId
            """)
    List<QuantityByKeyProjection> sumOpenQtyByCompanyItem();

    //調減被拒絕時列出受影響的 SO
    @Query("""
            SELECT DISTINCT reservation.salesOrderNumber
            FROM InventoryReservation reservation
            WHERE reservation.companyItemId = :companyItemId AND reservation.openQty > 0
            ORDER BY reservation.salesOrderNumber
            """)
    List<String> findOpenSalesOrderNumbers(@Param("companyItemId") Long companyItemId);

}
