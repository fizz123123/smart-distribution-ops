package com.smartdistribution.masterdata.supplier.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 須送出全部可編輯欄位；可為空的欄位傳 null 代表清空（D-046）。
 */
public record UpdateSupplierRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @Pattern(regexp = "^(\\d{8})?$")
        String taxId,

        @Size(max = 50)
        String contactName,

        @Size(max = 30)
        String phone,

        @Email
        @Size(max = 254)
        String email,

        @Size(max = 200)
        String address,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
