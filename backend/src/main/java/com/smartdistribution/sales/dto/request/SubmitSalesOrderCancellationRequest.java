package com.smartdistribution.sales.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitSalesOrderCancellationRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
