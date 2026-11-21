package com.smartdistribution.analytics.sales.mapper;

import com.smartdistribution.analytics.sales.dto.response.CancellationSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.NewOrderSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.RevenueSourceResponse;
import com.smartdistribution.analytics.sales.repository.projection.CancellationSourceProjection;
import com.smartdistribution.analytics.sales.repository.projection.NewOrderSourceProjection;
import com.smartdistribution.analytics.sales.repository.projection.RevenueSourceProjection;
import org.springframework.stereotype.Component;

/**
 * 來源明細的查詢結果轉成回應；欄位一對一，查詢結果不直接當回應，和其他模組一樣經過 mapper
 */
@Component
public class SalesSourceMapper {

    public RevenueSourceResponse toResponse(RevenueSourceProjection row) {
        return new RevenueSourceResponse(
                row.shipmentDate(),
                row.receivableId(),
                row.receivableNumber(),
                row.deliveryOrderId(),
                row.doNumber(),
                row.salesOrderId(),
                row.soNumber(),
                row.customerId(),
                row.customerCode(),
                row.customerName(),
                row.companyItemId(),
                row.itemCode(),
                row.itemName(),
                row.quantity(),
                row.unit(),
                row.unitPrice(),
                row.amount()
        );
    }

    public NewOrderSourceResponse toResponse(NewOrderSourceProjection row) {
        return new NewOrderSourceResponse(
                row.approvedDate(),
                row.salesOrderId(),
                row.soNumber(),
                row.customerId(),
                row.customerCode(),
                row.customerName(),
                row.responsibleSalesUserId(),
                row.responsibleSalesName(),
                row.amount()
        );
    }

    public CancellationSourceResponse toResponse(CancellationSourceProjection row) {
        return new CancellationSourceResponse(
                row.approvedDate(),
                row.cancellationId(),
                row.cancellationNumber(),
                row.salesOrderId(),
                row.soNumber(),
                row.customerId(),
                row.customerCode(),
                row.customerName(),
                row.companyItemId(),
                row.itemCode(),
                row.itemName(),
                row.quantity(),
                row.unit(),
                row.amount()
        );
    }
}
