package com.smartdistribution.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ReplaceUserRolesRequest(
        @NotNull
        @Size(min = 1, max = 10)
        Set<@NotBlank String> roleCodes,

        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
