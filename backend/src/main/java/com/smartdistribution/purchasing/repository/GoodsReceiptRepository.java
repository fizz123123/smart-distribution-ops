package com.smartdistribution.purchasing.repository;

import com.smartdistribution.purchasing.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    //不指定 PO 時，列出全部收貨紀錄
    @Query("""
            SELECT receipt
            FROM GoodsReceipt receipt
            WHERE :purchaseOrderId IS NULL OR receipt.purchaseOrder.id = :purchaseOrderId
            """)
    Page<GoodsReceipt> search(@Param("purchaseOrderId") Long purchaseOrderId, Pageable pageable);

    List<GoodsReceipt> findAllByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    boolean existsByPurchaseOrderId(Long purchaseOrderId);
}
