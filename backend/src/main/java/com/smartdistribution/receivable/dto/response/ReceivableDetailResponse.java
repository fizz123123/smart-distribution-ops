package com.smartdistribution.receivable.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.receivable.service.DueStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 應收詳情：金額一律是 TWD；表頭是含稅，明細是未稅
 *
 * @param dueStatus 即將到期或逾期；都不是、或已收清時是 null
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
        DueStatus dueStatus,
        BigDecimal untaxedAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        ExternalInvoice externalInvoice,
        Confirmation confirmation,
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

    public record UserRef(
            Long id,
            String name
    ) {
    }

    //還沒確認時整個是 null
    public record ExternalInvoice(
            String number,
            LocalDate date
    ) {
    }

    //誰在什麼時候確認成正式應收；還沒確認時是 null
    public record Confirmation(
            UserRef by,
            Instant at
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
