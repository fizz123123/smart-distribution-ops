package com.smartdistribution.purchasing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 收貨單詳情；不含 PO 單價，倉管也能查看
 */
public record GoodsReceiptResponse(
        Long id,
        String receiptNumber,
        Long purchaseOrderId,
        String poNumber,
        String supplierCode,
        String supplierName,
        LocalDate receivedDate,
        Long postedBy,
        String postedByName,
        Instant postedAt,
        List<Line> lines
) {

    /**
     * 數量以 PO 明細的單位表示；acceptedBaseQty 是入庫的基準數量
     */
    public record Line(
            Long id,
            Long purchaseOrderLineId,
            Integer lineNo,
            Long companyItemId,
            String itemCode,
            String itemName,
            UnitOfMeasure unit,
            BigDecimal receivedQty,
            BigDecimal acceptedQty,
            BigDecimal damagedQty,
            BigDecimal nonconformingQty,
            BigDecimal acceptedBaseQty,
            String exceptionReason
    ) {
    }
}
