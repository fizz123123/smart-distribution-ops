package com.smartdistribution.receivable.repository;

import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {

    boolean existsByDeliveryOrderId(Long deliveryOrderId);

    //關鍵字比對應收編號、DO 編號、SO 編號
    @Query("""
            SELECT receivable
            FROM Receivable receivable
            WHERE (:status IS NULL OR receivable.status = :status)
              AND (:customerId IS NULL OR receivable.customerId = :customerId)
              AND (:keyword IS NULL
                   OR receivable.receivableNumber LIKE CONCAT('%', :keyword, '%')
                   OR receivable.doNumber LIKE CONCAT('%', :keyword, '%')
                   OR receivable.soNumber LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Receivable> search(
            @Param("keyword") String keyword,
            @Param("status") ReceivableStatus status,
            @Param("customerId") Long customerId,
            Pageable pageable
    );
}
