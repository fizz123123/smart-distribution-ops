package com.smartdistribution.inventory.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdjustmentLineRequest(
        @NotNull
        Long companyItemId,

        //庫存基準單位的增減量，增加為正、減少為負；實際小數位再依單位檢查
        @NotNull
        @Digits(integer = 12, fraction = 3)
        BigDecimal quantityChangeBase
) {
}
