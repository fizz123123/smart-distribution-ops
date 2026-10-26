package com.smartdistribution.masterdata.companyitem.dto.response;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 詳情需同時呈現基準單位、安全庫存、偏好供應商與換算倍率（12 號文件 §5.2）。
 * 安全庫存另有自己的 API，所以 InventoryPolicyResponse 是獨立檔案；其他子結構只在這裡使用。
 */
public record CompanyItemDetailResponse(
        Long id,
        String itemCode,
        String name,
        UnitOfMeasure baseUnit,
        PreferredSupplier preferredSupplier,
        MasterDataStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<UnitConversion> unitConversions,
        InventoryPolicyResponse inventoryPolicy
) {

    public record PreferredSupplier(
            Long id,
            String supplierCode,
            String name,
            MasterDataStatus status
    ) {
    }

    // baseUnit 為 true 的那一筆是庫存基準單位，倍率固定為 1
    public record UnitConversion(
            UnitOfMeasure unit,
            BigDecimal factor,
            boolean baseUnit
    ) {
    }
}
