package com.smartdistribution.masterdata.supplier.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitSupplierRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
