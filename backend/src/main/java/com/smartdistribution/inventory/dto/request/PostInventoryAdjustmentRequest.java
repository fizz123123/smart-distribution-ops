package com.smartdistribution.inventory.dto.request;

import com.smartdistribution.inventory.entity.AdjustmentReasonType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostInventoryAdjustmentRequest(
        @NotNull
        AdjustmentReasonType reasonType,

        @NotBlank
        @Size(max = 500)
        String description,

        @NotEmpty
        List<@Valid @NotNull AdjustmentLineRequest> lines
) {
}
