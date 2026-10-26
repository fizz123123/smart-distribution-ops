package com.smartdistribution.sales.repository;

import com.smartdistribution.sales.entity.SalesOrderCancellation;
import com.smartdistribution.sales.entity.SalesOrderCancellationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SalesOrderCancellationRepository extends JpaRepository<SalesOrderCancellation, Long> {

    boolean existsBySalesOrderIdAndStatusIn(Long salesOrderId, Collection<SalesOrderCancellationStatus> statuses);

    List<SalesOrderCancellation> findAllBySalesOrderIdOrderByIdDesc(Long salesOrderId);
}
