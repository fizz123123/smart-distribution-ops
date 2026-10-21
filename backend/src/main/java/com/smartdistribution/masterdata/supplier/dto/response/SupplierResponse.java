package com.smartdistribution.masterdata.supplier.dto.response;

import com.smartdistribution.masterdata.common.MasterDataStatus;

import java.time.Instant;

public record SupplierResponse(
        Long id,
        String supplierCode,
        String name,
        String taxId,
        String contactName,
        String phone,
        String email,
        String address,
        MasterDataStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
