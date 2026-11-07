package com.smartdistribution.receivable.service.impl;

import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.entity.ReceivableLine;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.receivable.mapper.ReceivableAssembler;
import com.smartdistribution.receivable.repository.ReceivableLineRepository;
import com.smartdistribution.receivable.repository.ReceivableRepository;
import com.smartdistribution.receivable.service.ReceivableService;
import com.smartdistribution.sales.entity.SalesOrder;
import com.smartdistribution.sales.service.SalesOrderAmounts;
import com.smartdistribution.sales.service.SalesOrderBillingService.SalesOrderForBilling;
import com.smartdistribution.sales.service.SalesOrderBillingService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivableServiceImpl implements ReceivableService {

    private final ReceivableRepository receivableRepository;
    private final ReceivableLineRepository receivableLineRepository;
    private final ReceivableAssembler receivableAssembler;
    private final SalesOrderBillingService salesOrderBillingService;

    @Override
    @PreAuthorize("hasAuthority('RECEIVABLE_VIEW')")
    public PageResponse<ReceivableSummaryResponse> findReceivables(
            String keyword,
            ReceivableStatus status,
            Long customerId,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        return PageResponse.from(receivableAssembler.toSummaries(
                receivableRepository.search(normalizedKeyword, status, customerId, pageable)
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('RECEIVABLE_VIEW')")
    public ReceivableDetailResponse findReceivable(Long receivableId) {
        return receivableAssembler.toDetail(findReceivableById(receivableId));
    }

    @Override
    @Transactional
    public void createForDelivery(PostedDelivery delivery) {
        //DO 已經擋了重複過帳，這裡和資料庫的 unique 是最後一道保護
        if (receivableRepository.existsByDeliveryOrderId(delivery.deliveryOrderId())) {
            throw new BusinessException(ErrorCode.RECEIVABLE_ALREADY_EXISTS);
        }

        SalesOrderForBilling salesOrder = salesOrderBillingService.findForBilling(delivery.salesOrderId());
        Map<Long, SalesOrderForBilling.Line> salesOrderLines = salesOrder.lines()
                .stream()
                .collect(Collectors.toMap(SalesOrderForBilling.Line::id, Function.identity()));

        //計價單位等於訂購單位，所以實際出貨量直接就是計價數量
        List<BigDecimal> lineAmounts = delivery.lines()
                .stream()
                .map(line -> SalesOrderAmounts.lineAmount(
                        line.actualQuantity(),
                        salesOrderLines.get(line.salesOrderLineId()).unitPriceExcludingTax()
                ))
                .toList();
        SalesOrder.Amounts amounts = SalesOrderAmounts.documentAmounts(lineAmounts);

        Receivable receivable = new Receivable(
                delivery.deliveryOrderId(),
                delivery.doNumber(),
                salesOrder.id(),
                salesOrder.soNumber(),
                salesOrder.customerId(),
                salesOrder.paymentTerm(),
                delivery.actualShipmentDate(),
                amounts.untaxed(),
                amounts.tax(),
                amounts.total()
        );

        //應收編號依 ID 產生，所以要先 insert 取得 id 再回填
        receivableRepository.saveAndFlush(receivable);
        receivable.assignNumber();

        for (int index = 0; index < delivery.lines().size(); index++) {
            PostedDelivery.Line line = delivery.lines().get(index);
            SalesOrderForBilling.Line salesOrderLine = salesOrderLines.get(line.salesOrderLineId());

            receivableLineRepository.save(new ReceivableLine(
                    receivable,
                    index + 1,
                    line.deliveryOrderLineId(),
                    line.salesOrderLineId(),
                    line.companyItemId(),
                    salesOrderLine.customerItemCode(),
                    line.actualQuantity(),
                    salesOrderLine.pricingUnit(),
                    salesOrderLine.unitPriceExcludingTax(),
                    lineAmounts.get(index)
            ));
        }
    }

    private Receivable findReceivableById(Long receivableId) {
        return receivableRepository.findById(receivableId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECEIVABLE_NOT_FOUND));
    }
}
