package com.smartdistribution.sales.service.impl;

import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse.Supply;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.entity.SalesOrderLine;
import com.smartdistribution.sales.mapper.SalesOrderAssembler;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import com.smartdistribution.sales.service.SalesOrderSupplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderSupplyServiceImpl implements SalesOrderSupplyService {

    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderAssembler salesOrderAssembler;

    @Override
    public List<ApprovedLineSupply> findApprovedLineSupplies(Long companyItemId) {
        //和缺料清單讀同一批明細、用同一個 findSupplies 算供應狀態
        List<SalesOrderLine> lines = salesOrderLineRepository.findApprovedLines(companyItemId);
        Map<Long, Supply> supplies = salesOrderAssembler.findSupplies(lines);

        return lines.stream()
                .map(line -> toApprovedLineSupply(line, supplies.get(line.getId())))
                .toList();
    }

    private static ApprovedLineSupply toApprovedLineSupply(SalesOrderLine line, Supply supply) {
        SalesOrder salesOrder = line.getSalesOrder();

        return new ApprovedLineSupply(
                salesOrder.getId(),
                salesOrder.getSoNumber(),
                line.getId(),
                line.getLineNo(),
                salesOrder.getCustomerId(),
                salesOrder.getResponsibleSalesUserId(),
                line.getCompanyItemId(),
                line.getDeliveryDate(),
                line.getBaseQty().subtract(supply.cancelledQty()).subtract(supply.shippedQty()),
                supply.reservedQty(),
                supply.plannedNotOrderedQty(),
                supply.inTransitQty(),
                supply.unplannedQty(),
                supply.purchaseOrders()
        );
    }
}
