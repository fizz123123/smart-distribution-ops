package com.smartdistribution.sales.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

//撤回不需要原因
public record WithdrawSalesOrderCancellationRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
