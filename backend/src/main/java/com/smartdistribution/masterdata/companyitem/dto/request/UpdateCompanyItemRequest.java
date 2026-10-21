package com.smartdistribution.masterdata.companyitem.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 須送出全部可編輯欄位；偏好供應商傳 null 代表清空（D-046）。
 */
public record UpdateCompanyItemRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9._/-]*\\s*$")
        String itemCode,

        @NotBlank
        @Size(max = 200)
        String name,

        @NotNull
        UnitOfMeasure baseUnit,

        @Positive
        Long preferredSupplierId,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
