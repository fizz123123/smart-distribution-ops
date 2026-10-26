package com.smartdistribution.sales.repository;

import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    //關鍵字比對 SO 編號與客戶訂單編號；客戶名稱請先用客戶篩選
    @Query("""
            SELECT salesOrder
            FROM SalesOrder salesOrder
            WHERE (:status IS NULL OR salesOrder.status = :status)
              AND (:customerId IS NULL OR salesOrder.customerId = :customerId)
              AND (:keyword IS NULL
                   OR salesOrder.soNumber LIKE CONCAT('%', :keyword, '%')
                   OR salesOrder.customerOrderNumber LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<SalesOrder> search(
            @Param("keyword") String keyword,
            @Param("status") SalesOrderStatus status,
            @Param("customerId") Long customerId,
            Pageable pageable
    );
}
