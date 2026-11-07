package com.smartdistribution.fulfillment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

//出貨過帳：實際出貨日可以補填以前的日期，不能填未來
public record PostDeliveryOrderRequest(
        @NotNull
        LocalDate actualShipmentDate,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
