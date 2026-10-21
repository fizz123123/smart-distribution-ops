package com.smartdistribution.masterdata.companyitem.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 整份取代基準單位以外的換算；空清單代表清除全部換算（D-047）。
 */
public record ReplaceUnitConversionsRequest(
        @NotNull
        List<@Valid UnitConversionRequest> unitConversions,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
