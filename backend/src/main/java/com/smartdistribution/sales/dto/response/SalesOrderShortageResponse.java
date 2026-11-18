package com.smartdistribution.sales.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 缺料清單的一筆：已核准 SO 中尚待規劃大於 0 的明細；給採購排 PO，不含價格
 *
 * @param unplannedQty        尚待規劃，庫存基準單位；算法和 SO 詳情相同
 * @param preferredSupplierId 料號的偏好供應商，批次產生 PO 草稿時當預設值；沒有設定時為 null
 */
public record SalesOrderShortageResponse(
        Long salesOrderId,
        String soNumber,
        Long salesOrderLineId,
        Integer lineNo,
        String customerCode,
        String customerName,
        Long companyItemId,
        String itemCode,
        String itemName,
        UnitOfMeasure baseUnit,
        BigDecimal unplannedQty,
        LocalDate deliveryDate,
        Long preferredSupplierId,
        String preferredSupplierCode,
        String preferredSupplierName
) {
}
