package com.smartdistribution.sales.service.impl;

import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.service.SalesOrderDemandService.SalesOrderLineDemand;
import com.smartdistribution.sales.service.SalesOrderDemandService;
import com.smartdistribution.sales.service.SalesOrderQuantities;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderDemandServiceImpl implements SalesOrderDemandService {

    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderQuantities salesOrderQuantities;
    private final InventoryService inventoryService;

    @Override
    public Map<Long, SalesOrderLineDemand> findDemands(Collection<Long> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }

        List<SalesOrderLine> lines = salesOrderLineRepository.findAllById(salesOrderLineIds);
        Map<Long, BigDecimal> cancelledByLine = salesOrderQuantities.findCancelledQty(salesOrderLineIds);
        Map<Long, ShipmentQuantity> shipmentsByLine = salesOrderQuantities.findShipments(salesOrderLineIds);
        Map<Long, BigDecimal> reservedByLine = inventoryService.findOpenQtyBySalesOrderLines(salesOrderLineIds);

        Map<Long, SalesOrderLineDemand> demands = new HashMap<>();

        for (SalesOrderLine line : lines) {
            SalesOrder salesOrder = line.getSalesOrder();
            boolean approved = salesOrder.getStatus() == SalesOrderStatus.APPROVED;

            //草稿還沒有送審快照，基準數量是空的；只有已核准的 SO 才計算剩餘需求
            //排在 DO 裡的數量客戶仍然要，所以用剩餘需求，不用可取消量
            BigDecimal remainingQty = approved
                    ? salesOrderQuantities.remainingQty(line, cancelledByLine, shipmentsByLine)
                    : BigDecimal.ZERO;

            demands.put(line.getId(), new SalesOrderLineDemand(
                    line.getId(),
                    salesOrder.getId(),
                    salesOrder.getSoNumber(),
                    line.getLineNo(),
                    line.getCompanyItemId(),
                    approved,
                    remainingQty,
                    reservedByLine.getOrDefault(line.getId(), BigDecimal.ZERO)
            ));
        }

        return demands;
    }
}
