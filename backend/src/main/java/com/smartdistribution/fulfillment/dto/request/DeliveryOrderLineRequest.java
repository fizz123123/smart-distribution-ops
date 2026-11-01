package com.smartdistribution.fulfillment.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 數量以 SO 明細的單位表示，不另外輸入單位
 */
public record DeliveryOrderLineRequest(
        @NotNull
        Long salesOrderLineId,

        //實際小數位再依單位檢查，例如「支」不允許小數
        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 3)
        BigDecimal plannedQuantity
) {
}
