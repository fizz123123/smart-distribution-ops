package com.smartdistribution.receivable.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 確認正式應收：填入外部系統開立的發票；號碼格式與日期範圍在服務層檢查
 */
public record ConfirmReceivableRequest(
        @NotBlank
        @Size(max = 20)
        String externalInvoiceNumber,

        @NotNull
        LocalDate externalInvoiceDate,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
