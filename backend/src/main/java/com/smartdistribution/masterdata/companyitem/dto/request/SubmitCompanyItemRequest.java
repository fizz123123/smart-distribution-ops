package com.smartdistribution.masterdata.companyitem.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitCompanyItemRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
