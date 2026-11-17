package com.smartdistribution.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更正調整：lineIds 是要沖銷的原明細，至少一筆；只沖銷這些明細，其他明細不受影響
 */
public record ReverseInventoryAdjustmentRequest(
        @NotEmpty
        @Size(max = 100)
        List<@NotNull Long> lineIds,

        @NotBlank
        @Size(max = 400)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
