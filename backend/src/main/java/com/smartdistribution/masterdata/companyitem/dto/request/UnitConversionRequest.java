package com.smartdistribution.masterdata.companyitem.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UnitConversionRequest(
        @NotNull
        UnitOfMeasure unit,

        // 1 個交易單位等於幾個基準單位；須大於 0，最多 3 位小數（D-044）
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 3)
        BigDecimal factor
) {
}
