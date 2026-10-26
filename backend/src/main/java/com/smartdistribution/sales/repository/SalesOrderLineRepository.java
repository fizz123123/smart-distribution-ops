package com.smartdistribution.sales.repository;

import com.smartdistribution.sales.entity.SalesOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    List<SalesOrderLine> findAllBySalesOrderIdOrderByLineNoAsc(Long salesOrderId);

    //草稿整批取代明細時使用；直接下 DELETE，避免和新明細的行號唯一約束衝突
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SalesOrderLine line WHERE line.salesOrder.id = :salesOrderId")
    void deleteAllBySalesOrderId(@Param("salesOrderId") Long salesOrderId);
}
