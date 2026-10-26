package com.smartdistribution.sales.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

//數量的單位固定是該 SO 明細的訂購單位；實際小數位再依單位檢查
public record CancellationLineRequest(
        @NotNull
        Long salesOrderLineId,

        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 3)
        BigDecimal quantity
) {
}
