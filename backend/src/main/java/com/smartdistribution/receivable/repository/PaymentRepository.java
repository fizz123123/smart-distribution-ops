package com.smartdistribution.receivable.repository;

import com.smartdistribution.receivable.entity.Payment;
import com.smartdistribution.receivable.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByClientRequestId(String clientRequestId);

    //作廢先鎖收款表頭：同一筆收款同時送兩次作廢，第二次會等第一次完成，再因狀態已是 VOIDED 被擋下
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM Payment payment WHERE payment.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    //關鍵字比對收款編號、外部參考號碼
    @Query("""
            SELECT payment
            FROM Payment payment
            WHERE (:status IS NULL OR payment.status = :status)
              AND (:customerId IS NULL OR payment.customerId = :customerId)
              AND (:keyword IS NULL
                   OR payment.paymentNumber LIKE CONCAT('%', :keyword, '%')
                   OR payment.referenceNumber LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Payment> search(
            @Param("keyword") String keyword,
            @Param("status") PaymentStatus status,
            @Param("customerId") Long customerId,
            Pageable pageable
    );
}
