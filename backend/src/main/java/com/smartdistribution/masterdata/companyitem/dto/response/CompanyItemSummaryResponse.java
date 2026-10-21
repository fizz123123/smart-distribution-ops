package com.smartdistribution.masterdata.companyitem.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.common.MasterDataStatus;

/**
 * 清單用。不含換算、安全庫存與偏好供應商名稱，避免一頁資料產生大量額外查詢。
 */
public record CompanyItemSummaryResponse(
        Long id,
        String itemCode,
        String name,
        UnitOfMeasure baseUnit,
        Long preferredSupplierId,
        MasterDataStatus status,
        Long version
) {
}
