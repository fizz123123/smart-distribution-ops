package com.smartdistribution.fulfillment.repository;

import com.smartdistribution.fulfillment.entity.DeliveryOrder;
import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    //關鍵字比對 DO 編號；要看某張 SO 的 DO 請用 salesOrderId 篩選
    @Query("""
            SELECT deliveryOrder
            FROM DeliveryOrder deliveryOrder
            WHERE (:status IS NULL OR deliveryOrder.status = :status)
              AND (:salesOrderId IS NULL OR deliveryOrder.salesOrderId = :salesOrderId)
              AND (:keyword IS NULL OR deliveryOrder.doNumber LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<DeliveryOrder> search(
            @Param("keyword") String keyword,
            @Param("status") DeliveryOrderStatus status,
            @Param("salesOrderId") Long salesOrderId,
            Pageable pageable
    );
}
