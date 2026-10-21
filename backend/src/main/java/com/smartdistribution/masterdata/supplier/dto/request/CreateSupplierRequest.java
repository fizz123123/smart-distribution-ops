package com.smartdistribution.masterdata.supplier.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        //8位數字統一編號，可為 null 或 空字串
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
        String address
) {
}
