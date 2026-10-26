package com.smartdistribution.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReverseInventoryAdjustmentRequest(
        @NotBlank
        @Size(max = 400)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
