package com.smartdistribution.masterdata.customer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitCustomerRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
