package com.smartdistribution.receivable.repository;

import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {

    boolean existsByDeliveryOrderId(Long deliveryOrderId);

    boolean existsByExternalInvoiceNumberAndIdNot(String externalInvoiceNumber, Long id);

    /**
     * 收款與作廢收款時鎖定要核銷的應收，一律依 ID 由小到大
     * 所有交易的取鎖順序相同，兩筆收款同時核銷同一批應收時不會互相等待造成死鎖
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT receivable FROM Receivable receivable WHERE receivable.id IN :ids ORDER BY receivable.id")
    List<Receivable> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    //登錄收款時給財務選：這個客戶已確認、還有未收餘額的應收，先到期的排前面
    @Query("""
            SELECT receivable
            FROM Receivable receivable
            WHERE receivable.customerId = :customerId
              AND receivable.status IN (
                  ReceivableStatus.OPEN,
                  ReceivableStatus.PARTIALLY_PAID
              )
            ORDER BY receivable.dueDate, receivable.id
            """)
    List<Receivable> findOutstandingByCustomerId(@Param("customerId") Long customerId);

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
