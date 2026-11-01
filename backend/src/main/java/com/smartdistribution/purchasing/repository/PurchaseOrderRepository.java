package com.smartdistribution.purchasing.repository;

import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

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

    //收貨先鎖定 PO 表頭：兩個倉管同時收同一張 PO 會依序執行，未結量才不會被重複計算
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT purchaseOrder FROM PurchaseOrder purchaseOrder WHERE purchaseOrder.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    Page<PurchaseOrder> findAllByStatusIn(Collection<PurchaseOrderStatus> statuses, Pageable pageable);
}
