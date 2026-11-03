package com.smartdistribution.fulfillment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

//完成揀貨：DO 上的每一筆都要回報一次
public record ReadyDeliveryOrderRequest(
        @NotEmpty
        List<@Valid @NotNull ActualQuantityRequest> lines,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
