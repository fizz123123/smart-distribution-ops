package com.smartdistribution.receivable.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 更正外部發票：只有號碼與日期可以改；送了金額、到期日等其他欄位也會被忽略
 */
public record CorrectExternalInvoiceRequest(
        @NotBlank
        @Size(max = 20)
        String externalInvoiceNumber,

        @NotNull
        LocalDate externalInvoiceDate,

        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
