package com.smartdistribution.masterdata.companyitem.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCompanyItemRequest(
        // 英數字開頭，可含 . _ / -；存檔前去除空白並轉大寫
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9._/-]*\\s*$")
        String itemCode,

        // 品名，包含材質與規格
        @NotBlank
        @Size(max = 200)
        String name,

        @NotNull
        UnitOfMeasure baseUnit,

        @Positive
        Long preferredSupplierId
) {
}
