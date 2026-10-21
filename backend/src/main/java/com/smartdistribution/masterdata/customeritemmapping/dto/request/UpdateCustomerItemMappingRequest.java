package com.smartdistribution.masterdata.customeritemmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 只有草稿與退回狀態可以修改；啟用後三個欄位都鎖定
 */
public record UpdateCustomerItemMappingRequest(
        @NotNull
        @Positive
        Long customerId,

        @Size(max = 50)
        String customerItemCode,

        @NotNull
        @Positive
        Long companyItemId,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
