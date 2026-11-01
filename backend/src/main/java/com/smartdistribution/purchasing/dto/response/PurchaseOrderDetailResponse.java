package com.smartdistribution.purchasing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.entity.PurchaseOrderLineStatus;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param untaxedAmount 送審後是保存的金額；草稿在每筆明細都有單價時試算，否則為 null
 */
public record PurchaseOrderDetailResponse(
        Long id,
        String poNumber,
        PurchaseOrderStatus status,
        SupplierRef supplier,
        LocalDate expectedArrivalDate,
        String remark,
        BigDecimal untaxedAmount,
        UserRef createdBy,
        Instant submittedAt,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<Line> lines
) {

    public record SupplierRef(
            Long id,
            String supplierCode,
            String name
    ) {
    }

    public record UserRef(
            Long id,
            String name
    ) {
    }

    /**
     * @param source 關聯 SO 時才有值；一般補庫為 null
     */
    public record Line(
            Long id,
            Integer lineNo,
            Long companyItemId,
            String itemCode,
            String itemName,
            UnitOfMeasure baseUnit,
            SalesOrderSource source,
            BigDecimal quantity,
            UnitOfMeasure unit,
            BigDecimal conversionFactor,
            BigDecimal baseQty,
            BigDecimal unitPriceExcludingTax,
            BigDecimal lineAmountExcludingTax,
            PurchaseOrderLineStatus status
    ) {
    }

    public record SalesOrderSource(
            Long salesOrderLineId,
            Long salesOrderId,
            String salesOrderNumber,
            Integer salesOrderLineNo
    ) {
    }
}
