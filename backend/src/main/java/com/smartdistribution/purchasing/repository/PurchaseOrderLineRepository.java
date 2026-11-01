package com.smartdistribution.purchasing.repository;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    List<PurchaseOrderLine> findAllByPurchaseOrderIdOrderByLineNoAsc(Long purchaseOrderId);

    //草稿整批取代明細時使用；直接下 DELETE，避免和新明細的行號唯一約束衝突
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PurchaseOrderLine line WHERE line.purchaseOrder.id = :purchaseOrderId")
    void deleteAllByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 關聯這些 SO 明細、PO 狀態在指定範圍內的訂購量與已取消量，依 SO 明細加總
     * 修改或核准某張 PO 時，用 excludedPurchaseOrderId 排除它自己；不需要排除時傳 0
     */
    @Query("""
            SELECT new com.smartdistribution.purchasing.repository.SalesOrderLineQuantityProjection(
                line.salesOrderLineId,
                SUM(line.baseQty),
                SUM(line.cancelledBaseQty)
            )
            FROM PurchaseOrderLine line
            JOIN line.purchaseOrder purchaseOrder
            WHERE line.salesOrderLineId IN :salesOrderLineIds
              AND purchaseOrder.status IN :statuses
              AND purchaseOrder.id <> :excludedPurchaseOrderId
            GROUP BY line.salesOrderLineId
            """)
    List<SalesOrderLineQuantityProjection> sumQtyBySalesOrderLines(
            @Param("salesOrderLineIds") Collection<Long> salesOrderLineIds,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses,
            @Param("excludedPurchaseOrderId") Long excludedPurchaseOrderId
    );

    //同一供應商、料號與單位最近一次核准的單價，新的在前；只需要第一筆
    @Query("""
            SELECT line.unitPriceExcludingTax
            FROM PurchaseOrderLine line
            JOIN line.purchaseOrder purchaseOrder
            WHERE purchaseOrder.supplierId = :supplierId
              AND line.companyItemId = :companyItemId
              AND line.unit = :unit
              AND purchaseOrder.approvedAt IS NOT NULL
            ORDER BY purchaseOrder.approvedAt DESC, purchaseOrder.id DESC
            """)
    List<BigDecimal> findApprovedPrices(
            @Param("supplierId") Long supplierId,
            @Param("companyItemId") Long companyItemId,
            @Param("unit") UnitOfMeasure unit,
            Pageable pageable
    );
}
