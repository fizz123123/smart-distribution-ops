package com.smartdistribution.masterdata.customeritemmapping.dto.response;

import com.smartdistribution.masterdata.common.MasterDataStatus;

import java.time.Instant;

public record CustomerItemMappingResponse(
        Long id,
        MappingCustomerResponse customer,
        String customerItemCode,
        MappingCompanyItemResponse companyItem,
        MasterDataStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
