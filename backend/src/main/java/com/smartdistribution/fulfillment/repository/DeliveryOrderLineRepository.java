package com.smartdistribution.fulfillment.repository;

import com.smartdistribution.fulfillment.entity.DeliveryOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeliveryOrderLineRepository extends JpaRepository<DeliveryOrderLine, Long> {

    List<DeliveryOrderLine> findAllByDeliveryOrderIdOrderByLineNoAsc(Long deliveryOrderId);

    /**
     * 關聯這些 SO 明細的 DO 數量，依 SO 明細與 DO 狀態分組加總；作廢的不算
     * 要用預計量還是實際量依狀態決定，在 ShipmentQuantityProviderImpl 裡算
     */
    @Query("""
            SELECT new com.smartdistribution.fulfillment.repository.ShipmentQuantityProjection(
                line.salesOrderLineId,
                deliveryOrder.status,
                SUM(line.plannedBaseQty),
                SUM(line.actualBaseQty)
            )
            FROM DeliveryOrderLine line
            JOIN line.deliveryOrder deliveryOrder
            WHERE line.salesOrderLineId IN :salesOrderLineIds
              AND deliveryOrder.status <> com.smartdistribution.fulfillment.entity.DeliveryOrderStatus.VOIDED
            GROUP BY line.salesOrderLineId, deliveryOrder.status
            """)
    List<ShipmentQuantityProjection> sumBySalesOrderLines(
            @Param("salesOrderLineIds") Collection<Long> salesOrderLineIds
    );
}
