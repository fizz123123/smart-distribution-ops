package com.smartdistribution.receivable.mapper;

import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.Receivable;
import com.smartdistribution.receivable.entity.ReceivableLine;
import com.smartdistribution.receivable.repository.ReceivableLineRepository;
import com.smartdistribution.receivable.service.ReceivableMasterData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 組出應收的回應，要查客戶與料號，所以不是單純的欄位轉換
 */
@Component
@RequiredArgsConstructor
public class ReceivableAssembler {

    private final ReceivableLineRepository receivableLineRepository;
    private final ReceivableMasterData receivableMasterData;

    //清單頁一次查好這一頁的客戶
    public Page<ReceivableSummaryResponse> toSummaries(Page<Receivable> receivables) {
        Map<Long, Customer> customers = receivableMasterData.findCustomers(
                receivables.stream().map(Receivable::getCustomerId).collect(Collectors.toSet())
        );

        return receivables.map(receivable -> {
            Customer customer = customers.get(receivable.getCustomerId());

            return new ReceivableSummaryResponse(
                    receivable.getId(),
                    receivable.getReceivableNumber(),
                    receivable.getStatus(),
                    receivable.getCustomerId(),
                    customer == null ? null : customer.getCustomerCode(),
                    customer == null ? null : customer.getName(),
                    receivable.getDoNumber(),
                    receivable.getSoNumber(),
                    receivable.getActualShipmentDate(),
                    receivable.getDueDate(),
                    receivable.getTotalAmount(),
                    receivable.getCreatedAt()
            );
        });
    }

    public ReceivableDetailResponse toDetail(Receivable receivable) {
        Customer customer = receivableMasterData.findCustomers(List.of(receivable.getCustomerId()))
                .get(receivable.getCustomerId());
        List<ReceivableLine> lines = receivableLineRepository.findAllByReceivableIdOrderByLineNoAsc(receivable.getId());
        Map<Long, CompanyItem> items = receivableMasterData.findCompanyItems(
                lines.stream().map(ReceivableLine::getCompanyItemId).collect(Collectors.toSet())
        );

        return new ReceivableDetailResponse(
                receivable.getId(),
                receivable.getReceivableNumber(),
                receivable.getStatus(),
                new ReceivableDetailResponse.CustomerRef(
                        customer.getId(),
                        customer.getCustomerCode(),
                        customer.getName()
                ),
                new ReceivableDetailResponse.DeliveryOrderRef(
                        receivable.getDeliveryOrderId(),
                        receivable.getDoNumber(),
                        receivable.getActualShipmentDate()
                ),
                new ReceivableDetailResponse.SalesOrderRef(
                        receivable.getSalesOrderId(),
                        receivable.getSoNumber()
                ),
                receivable.getPaymentTerm(),
                receivable.getDueDate(),
                receivable.getUntaxedAmount(),
                receivable.getTaxAmount(),
                receivable.getTotalAmount(),
                receivable.getCreatedAt(),
                receivable.getUpdatedAt(),
                receivable.getVersion(),
                lines.stream().map(line -> toLine(line, items.get(line.getCompanyItemId()))).toList()
        );
    }

    private ReceivableDetailResponse.Line toLine(ReceivableLine line, CompanyItem item) {
        return new ReceivableDetailResponse.Line(
                line.getId(),
                line.getLineNo(),
                line.getDeliveryOrderLineId(),
                line.getSalesOrderLineId(),
                line.getCompanyItemId(),
                item.getItemCode(),
                item.getName(),
                line.getCustomerItemCode(),
                line.getQuantity(),
                line.getUnit(),
                line.getUnitPriceExcludingTax(),
                line.getLineAmountExcludingTax()
        );
    }
}
