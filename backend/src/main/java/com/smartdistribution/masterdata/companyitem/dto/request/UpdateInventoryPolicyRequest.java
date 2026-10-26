package com.smartdistribution.masterdata.companyitem.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateInventoryPolicyRequest(
        // 以庫存基準單位輸入，最多 3 位小數（D-044）
        @NotNull
        @DecimalMin("0")
        @Digits(integer = 12, fraction = 3)
        BigDecimal safetyStockBaseQty,

        @NotBlank
        @Size(max = 500)
        String reason,

        // 安全庫存政策自己的版本，和公司料號的版本分開
        @NotNull
        @PositiveOrZero
        Long version
) {
}
