package com.smartdistribution.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank
        @Size(max = 20)
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 20)
        String newPassword
) {
}
