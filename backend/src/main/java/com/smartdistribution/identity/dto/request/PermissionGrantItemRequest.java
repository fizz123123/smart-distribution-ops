package com.smartdistribution.identity.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PermissionGrantItemRequest(
        @NotBlank(message = "權限代碼不可空白")
        String permissionCode,

        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        Instant validFrom,

        Instant validTo
) {

    @AssertTrue(message = "授權開始時間必須早於結束時間")
    public boolean isPeriodValid() {
        if (validFrom == null || validTo == null) {
            return true;
        }
        return validFrom.isBefore(validTo);
    }
}
