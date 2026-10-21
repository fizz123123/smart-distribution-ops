package com.smartdistribution.masterdata.companyitem.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import java.math.BigDecimal;

public record UnitConversionResponse(
        UnitOfMeasure unit,
        BigDecimal factor,
        boolean baseUnit
) {
}
