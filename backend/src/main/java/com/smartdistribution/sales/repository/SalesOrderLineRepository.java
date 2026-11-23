package com.smartdistribution.sales.repository;

import com.smartdistribution.sales.entity.SalesOrderLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    List<SalesOrderLine> findAllBySalesOrderIdOrderByLineNoAsc(Long salesOrderId);

    //DO 清單一次取回這一頁所有 SO 的明細
    List<SalesOrderLine> findAllBySalesOrderIdInOrderByLineNoAsc(Collection<Long> salesOrderIds);

    //缺料清單：所有已核准 SO 的明細，連同表頭一起載入，依交期排序
    @EntityGraph(attributePaths = "salesOrder")
    @Query("""
            SELECT line
            FROM SalesOrderLine line
            WHERE line.salesOrder.status = com.smartdistribution.sales.entity.SalesOrderStatus.APPROVED
              AND (:companyItemId IS NULL OR line.companyItemId = :companyItemId)
            ORDER BY line.deliveryDate ASC, line.salesOrder.id ASC, line.lineNo ASC
            """)
    List<SalesOrderLine> findApprovedLines(@Param("companyItemId") Long companyItemId);

    //儀表板：指定的明細中屬於已核准 SO 的，連同表頭一起載入，依交期排序
    @EntityGraph(attributePaths = "salesOrder")
    @Query("""
            SELECT line
            FROM SalesOrderLine line
            WHERE line.id IN :salesOrderLineIds
              AND line.salesOrder.status = com.smartdistribution.sales.entity.SalesOrderStatus.APPROVED
            ORDER BY line.deliveryDate ASC, line.salesOrder.id ASC, line.lineNo ASC
            """)
    List<SalesOrderLine> findApprovedLinesByIds(@Param("salesOrderLineIds") Collection<Long> salesOrderLineIds);

    //草稿整批取代明細時使用；直接下 DELETE，避免和新明細的行號唯一約束衝突
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SalesOrderLine line WHERE line.salesOrder.id = :salesOrderId")
    void deleteAllBySalesOrderId(@Param("salesOrderId") Long salesOrderId);
}
