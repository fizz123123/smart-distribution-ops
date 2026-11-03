package com.smartdistribution.masterdata.companyitem.dto.request;

import com.smartdistribution.shared.web.request.ReasonRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReasonRequest(
        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
