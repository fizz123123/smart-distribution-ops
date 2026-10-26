package com.smartdistribution.sales.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitSalesOrderRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
