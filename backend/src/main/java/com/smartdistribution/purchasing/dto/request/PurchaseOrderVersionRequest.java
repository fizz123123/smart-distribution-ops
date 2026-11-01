package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

//送審與放棄草稿只需要帶目前的版本號
public record PurchaseOrderVersionRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
