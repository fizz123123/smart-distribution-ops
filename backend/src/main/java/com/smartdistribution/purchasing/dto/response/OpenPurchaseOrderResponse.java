package com.smartdistribution.purchasing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 倉管的待收貨清單：已核准、還有未結數量的 PO；不含單價
 */
public record OpenPurchaseOrderResponse(
        Long id,
        String poNumber,
        PurchaseOrderStatus status,
        String supplierCode,
        String supplierName,
        LocalDate expectedArrivalDate,
        List<Line> lines
) {

    /**
     * 數量以 PO 明細的單位表示，倉管照這個單位點貨
     *
     * @param openQty 還能收的數量；為 0 的明細已收齊或不再等
     */
    public record Line(
            Long purchaseOrderLineId,
            Integer lineNo,
            Long companyItemId,
            String itemCode,
            String itemName,
            UnitOfMeasure unit,
            BigDecimal quantity,
            BigDecimal acceptedQty,
            BigDecimal openQty
    ) {
    }
}
