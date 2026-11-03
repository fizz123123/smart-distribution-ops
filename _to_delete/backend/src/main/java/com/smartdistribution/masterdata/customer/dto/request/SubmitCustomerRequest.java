package com.smartdistribution.masterdata.customer.dto.request;

import com.smartdistribution.shared.web.request.VersionRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record VersionRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
