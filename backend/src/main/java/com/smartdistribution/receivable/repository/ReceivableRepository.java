package com.smartdistribution.receivable.repository;

import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {

    boolean existsByDeliveryOrderId(Long deliveryOrderId);

    boolean existsByExternalInvoiceNumberAndIdNot(String externalInvoiceNumber, Long id);

    /**
     * 關鍵字比對應收編號、DO 編號、SO 編號、發票號碼
     * 到期日範圍用來篩即將到期與逾期，範圍由 DueStatus 決定
     */
    @Query("""
            SELECT receivable
            FROM Receivable receivable
            WHERE (:status IS NULL OR receivable.status = :status)
              AND (:customerId IS NULL OR receivable.customerId = :customerId)
              AND (:keyword IS NULL
                   OR receivable.receivableNumber LIKE CONCAT('%', :keyword, '%')
                   OR receivable.doNumber LIKE CONCAT('%', :keyword, '%')
                   OR receivable.soNumber LIKE CONCAT('%', :keyword, '%')
                   OR receivable.externalInvoiceNumber LIKE CONCAT('%', :keyword, '%'))
              AND (:dueFrom IS NULL OR receivable.dueDate >= :dueFrom)
              AND (:dueTo IS NULL OR receivable.dueDate <= :dueTo)
              AND (:unpaidOnly = false
                   OR receivable.status <> ReceivableStatus.PAID)
            """)
    Page<Receivable> search(
            @Param("keyword") String keyword,
            @Param("status") ReceivableStatus status,
            @Param("customerId") Long customerId,
            @Param("dueFrom") LocalDate dueFrom,
            @Param("dueTo") LocalDate dueTo,
            @Param("unpaidOnly") boolean unpaidOnly,
            Pageable pageable
    );
}
