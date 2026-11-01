package com.smartdistribution.purchasing.repository;

import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    //關鍵字比對 PO 編號；供應商請用供應商篩選
    @Query("""
            SELECT purchaseOrder
            FROM PurchaseOrder purchaseOrder
            WHERE (:status IS NULL OR purchaseOrder.status = :status)
              AND (:supplierId IS NULL OR purchaseOrder.supplierId = :supplierId)
              AND (:keyword IS NULL OR purchaseOrder.poNumber LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<PurchaseOrder> search(
            @Param("keyword") String keyword,
            @Param("status") PurchaseOrderStatus status,
            @Param("supplierId") Long supplierId,
            Pageable pageable
    );
}
