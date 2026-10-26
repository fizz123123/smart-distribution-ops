package com.smartdistribution.sales.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

//放棄草稿不需要原因
public record CancelSalesOrderRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
