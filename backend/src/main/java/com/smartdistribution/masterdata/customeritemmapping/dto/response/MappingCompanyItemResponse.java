package com.smartdistribution.masterdata.customeritemmapping.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

public record MappingCompanyItemResponse(
        Long id,
        String itemCode,
        String name,
        UnitOfMeasure baseUnit
) {
}
