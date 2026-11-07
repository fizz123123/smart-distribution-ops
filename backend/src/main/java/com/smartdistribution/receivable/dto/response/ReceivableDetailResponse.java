package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.receivable.entity.ReceivableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 應收詳情：金額一律是 TWD；表頭是含稅，明細是未稅
 */
public record ReceivableDetailResponse(
        Long id,
        String receivableNumber,
        ReceivableStatus status,
        CustomerRef customer,
        DeliveryOrderRef deliveryOrder,
        SalesOrderRef salesOrder,
        PaymentTerm paymentTerm,
        LocalDate dueDate,
        BigDecimal untaxedAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<Line> lines
) {

    public record CustomerRef(
            Long id,
            String customerCode,
            String name
    ) {
    }

    public record DeliveryOrderRef(
            Long id,
            String doNumber,
            LocalDate actualShipmentDate
    ) {
    }

    public record SalesOrderRef(
            Long id,
            String soNumber
    ) {
    }

    /**
     * @param quantity 實際出貨量，以 SO 明細的單位表示
     */
    public record Line(
            Long id,
            Integer lineNo,
            Long deliveryOrderLineId,
            Long salesOrderLineId,
            Long companyItemId,
            String itemCode,
            String itemName,
            String customerItemCode,
            BigDecimal quantity,
            UnitOfMeasure unit,
            BigDecimal unitPriceExcludingTax,
            BigDecimal lineAmountExcludingTax
    ) {
    }
}
