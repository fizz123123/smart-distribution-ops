package com.smartdistribution.fulfillment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

//撤回出貨通知只需要帶目前的版本號
public record DeliveryOrderVersionRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
