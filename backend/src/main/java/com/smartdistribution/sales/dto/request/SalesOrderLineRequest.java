package com.smartdistribution.sales.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SalesOrderLineRequest(
        @NotNull
        Long customerItemMappingId,

        //實際小數位再依單位檢查，例如 "支" 不允許小數
        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 3)
        BigDecimal quantity,

        //必須是有 ACTIVE 價格的單位，送審時才檢查
        @NotNull
        UnitOfMeasure unit
) {
}
