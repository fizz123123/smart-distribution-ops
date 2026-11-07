package com.smartdistribution.receivable.repository;

import com.smartdistribution.receivable.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findAllByPaymentIdOrderByIdAsc(Long paymentId);

    List<PaymentAllocation> findAllByReceivableIdOrderByIdAsc(Long receivableId);

    //只加總 CONFIRMED 收款的核銷；作廢的收款保留核銷明細，但不算已收
    @Query("""
            SELECT new com.smartdistribution.receivable.repository.PaidAmountProjection(
                allocation.receivable.id,
                SUM(allocation.allocatedAmount)
            )
            FROM PaymentAllocation allocation
            WHERE allocation.receivable.id IN :receivableIds
              AND allocation.payment.status = com.smartdistribution.receivable.entity.PaymentStatus.CONFIRMED
            GROUP BY allocation.receivable.id
            """)
    List<PaidAmountProjection> sumPaidByReceivableIds(@Param("receivableIds") Collection<Long> receivableIds);
}
