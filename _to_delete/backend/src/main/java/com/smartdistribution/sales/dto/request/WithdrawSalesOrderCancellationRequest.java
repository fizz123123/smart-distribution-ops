package com.smartdistribution.sales.dto.request;

import com.smartdistribution.shared.web.request.VersionRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

//撤回不需要原因
public record VersionRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
