package com.smartdistribution.approval.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnRequest(
        @NotBlank
        @Size(max = 500)
        String comment
) {
}
