package com.smartdistribution.sales.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSalesOrderCancellationRequest(
        @NotBlank
        @Size(max = 500)
        String reason,

        @NotEmpty
        @Size(max = 100)
        List<@Valid @NotNull CancellationLineRequest> lines
) {
}
