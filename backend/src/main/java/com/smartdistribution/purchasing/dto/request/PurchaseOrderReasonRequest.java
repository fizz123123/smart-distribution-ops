package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

//取消已核准 PO 與短收結案共用：原因必填，並帶目前的版本號
public record PurchaseOrderReasonRequest(
        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
