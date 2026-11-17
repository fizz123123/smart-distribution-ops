package com.smartdistribution.pricing.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateStandardPriceRequest(
        @NotNull
        @Positive
        Long companyItemId,

        //須為料號的庫存基準單位或已設定換算的單位
        @NotNull
        UnitOfMeasure pricingUnit,

        //未稅 TWD 單價，須大於 0，最多 2 位小數
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 13, fraction = 2)
        BigDecimal unitPriceExcludingTax,

        //不填代表核准當天生效；可以填未來日期預先排定調價
        LocalDate effectiveFrom,

        //不填代表不限期，直到下一個版本接手
        LocalDate effectiveTo
) {
}
