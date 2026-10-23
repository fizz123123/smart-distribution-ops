package com.smartdistribution.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReverseInventoryAdjustmentRequest(
        @NotBlank
        @Size(max = 400)
        String reason,

        // 原調整的版本，避免兩個人同時更正同一張調整
        @NotNull
        @PositiveOrZero
        Long version
) {
}
