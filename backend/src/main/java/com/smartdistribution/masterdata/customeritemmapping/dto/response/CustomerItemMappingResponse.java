package com.smartdistribution.masterdata.customeritemmapping.dto.response;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.time.Instant;

public record CustomerItemMappingResponse(
        Long id,
        CustomerRef customer,
        String customerItemCode,
        CompanyItemRef companyItem,
        MasterDataStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {

    public record CustomerRef(
            Long id,
            String customerCode,
            String name
    ) {
    }

    public record CompanyItemRef(
            Long id,
            String itemCode,
            String name,
            UnitOfMeasure baseUnit
    ) {
    }
}
