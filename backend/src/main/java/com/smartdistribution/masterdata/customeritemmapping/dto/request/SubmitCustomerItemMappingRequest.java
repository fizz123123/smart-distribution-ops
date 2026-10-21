package com.smartdistribution.masterdata.customeritemmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitCustomerItemMappingRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
