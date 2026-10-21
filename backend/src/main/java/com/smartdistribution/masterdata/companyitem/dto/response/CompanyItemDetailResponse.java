package com.smartdistribution.masterdata.companyitem.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.inventory.dto.response.InventoryPolicyResponse;
import com.smartdistribution.masterdata.common.MasterDataStatus;

import java.time.Instant;
import java.util.List;

/**
 * 詳情需同時呈現基準單位、安全庫存、偏好供應商與換算倍率（12 號文件 §5.2）。
 */
public record CompanyItemDetailResponse(
        Long id,
        String itemCode,
        String name,
        UnitOfMeasure baseUnit,
        PreferredSupplierResponse preferredSupplier,
        MasterDataStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<UnitConversionResponse> unitConversions,
        InventoryPolicyResponse inventoryPolicy
) {
}
