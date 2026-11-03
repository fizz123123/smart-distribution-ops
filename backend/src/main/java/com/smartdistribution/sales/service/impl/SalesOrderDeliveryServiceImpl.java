package com.smartdistribution.sales.service.impl;

import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.repository.SalesOrderRepository;
import com.smartdistribution.sales.service.SalesOrderDeliveryService.SalesOrderForDelivery;
import com.smartdistribution.sales.service.SalesOrderDeliveryService;
import com.smartdistribution.sales.service.SalesOrderQuantities;
import com.smartdistribution.sales.service.ShipmentQuantityProvider.ShipmentQuantity;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderDeliveryServiceImpl implements SalesOrderDeliveryService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderQuantities salesOrderQuantities;

    @Override
    public Map<Long, SalesOrderForDelivery> findForDelivery(Collection<Long> salesOrderIds) {
        if (salesOrderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<SalesOrderLine>> linesByOrder = salesOrderLineRepository
                .findAllBySalesOrderIdInOrderByLineNoAsc(salesOrderIds)
                .stream()
                .collect(Collectors.groupingBy(line -> line.getSalesOrder().getId()));

        Map<Long, SalesOrderForDelivery> result = new HashMap<>();

        for (SalesOrder salesOrder : salesOrderRepository.findAllById(salesOrderIds)) {
            List<SalesOrderForDelivery.Line> lines = linesByOrder.getOrDefault(salesOrder.getId(), List.of())
                    .stream()
                    .map(line -> new SalesOrderForDelivery.Line(
                            line.getId(),
                            line.getLineNo(),
                            line.getCompanyItemId(),
                            line.getCustomerItemCode(),
                            line.getUnit(),
                            line.getConversionFactor()
                    ))
                    .toList();

            result.put(salesOrder.getId(), new SalesOrderForDelivery(
                    salesOrder.getId(),
                    salesOrder.getSoNumber(),
                    salesOrder.getStatus() == SalesOrderStatus.APPROVED,
                    salesOrder.getCustomerId(),
                    salesOrder.getCustomerOrderNumber(),
                    salesOrder.getDeliveryMethod(),
                    salesOrder.getDeliveryAddress(),
                    salesOrder.getContactName(),
                    salesOrder.getContactPhone(),
                    lines
            ));
        }

        return result;
    }

    @Override
    @Transactional
    public void closeIfFulfilled(Long salesOrderId) {
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND));
        if (salesOrder.getStatus() != SalesOrderStatus.APPROVED) {
            return;
        }

        List<SalesOrderLine> lines = salesOrderLineRepository.findAllBySalesOrderIdOrderByLineNoAsc(salesOrderId);
        List<Long> lineIds = lines.stream().map(SalesOrderLine::getId).toList();
        Map<Long, BigDecimal> cancelledByLine = salesOrderQuantities.findCancelledQty(lineIds);
        Map<Long, ShipmentQuantity> shipmentsByLine = salesOrderQuantities.findShipments(lineIds);

        boolean nothingRemaining = lines.stream()
                .allMatch(line -> salesOrderQuantities.remainingQty(line, cancelledByLine, shipmentsByLine).signum() <= 0);
        if (nothingRemaining) {
            salesOrder.close();
        }
    }
}
