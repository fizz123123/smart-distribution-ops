package com.smartdistribution.shared.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 需要填原因、並帶目前版本號的操作共用，例如停用、啟用、作廢、取消
 */
public record ReasonRequest(
        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
