package com.smartdistribution.sales.service.impl;

import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.repository.SalesOrderLineRepository;
import com.smartdistribution.sales.repository.SalesOrderRepository;
import com.smartdistribution.sales.service.SalesOrderBillingService.SalesOrderForBilling;
import com.smartdistribution.sales.service.SalesOrderBillingService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderBillingServiceImpl implements SalesOrderBillingService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;

    @Override
    public SalesOrderForBilling findForBilling(Long salesOrderId) {
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALES_ORDER_NOT_FOUND));

        return new SalesOrderForBilling(
                salesOrder.getId(),
                salesOrder.getSoNumber(),
                salesOrder.getCustomerId(),
                salesOrder.getPaymentTerm(),
                salesOrderLineRepository.findAllBySalesOrderIdOrderByLineNoAsc(salesOrderId)
                        .stream()
                        .map(line -> new SalesOrderForBilling.Line(
                                line.getId(),
                                line.getCustomerItemCode(),
                                line.getPricingUnit(),
                                line.getUnitPriceExcludingTax()
                        ))
                        .toList()
        );
    }
}
