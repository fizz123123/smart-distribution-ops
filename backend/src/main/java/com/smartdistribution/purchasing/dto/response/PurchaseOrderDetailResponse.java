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
        List<Line> lines,
        List<ReceiptRef> receipts
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
     * @param source          關聯 SO 時才有值；一般補庫為 null
     * @param acceptedBaseQty 累計合格入庫（基準單位）
     * @param openBaseQty     未結量（基準單位）：訂購量 － 累計合格入庫 － 不再等的數量
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
            BigDecimal acceptedBaseQty,
            BigDecimal openBaseQty,
            PurchaseOrderLineStatus status
    ) {
    }

    //這張 PO 的收貨紀錄，詳細內容另外查詢收貨單
    public record ReceiptRef(
            Long id,
            String receiptNumber,
            LocalDate receivedDate
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
