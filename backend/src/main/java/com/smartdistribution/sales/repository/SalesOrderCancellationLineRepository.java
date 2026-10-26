package com.smartdistribution.sales.repository;

import com.smartdistribution.sales.entity.SalesOrderCancellationLine;
import com.smartdistribution.sales.entity.SalesOrderCancellationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOrderCancellationLineRepository extends JpaRepository<SalesOrderCancellationLine, Long> {

    List<SalesOrderCancellationLine> findAllByCancellationIdOrderByIdAsc(Long cancellationId);

    //草稿整批取代明細時使用，做法同 SO 明細
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SalesOrderCancellationLine line WHERE line.cancellation.id = :cancellationId")
    void deleteAllByCancellationId(@Param("cancellationId") Long cancellationId);

    //只算已核准的取消；草稿、待核准、退回與撤回的申請都還沒有減少有效訂購量
    @Query("""
            SELECT new com.smartdistribution.sales.repository.CancelledQuantityProjection(
                line.salesOrderLine.id,
                SUM(line.baseQty)
            )
            FROM SalesOrderCancellationLine line
            WHERE line.cancellation.status = :approved
              AND line.salesOrderLine.id IN :salesOrderLineIds
            GROUP BY line.salesOrderLine.id
            """)
    List<CancelledQuantityProjection> sumCancelledQty(
            @Param("salesOrderLineIds") Collection<Long> salesOrderLineIds,
            @Param("approved") SalesOrderCancellationStatus approved
    );
}
