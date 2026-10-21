package com.smartdistribution.masterdata.customeritemmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCustomerItemMappingRequest(
        @NotNull
        @Positive
        Long customerId,

        //選填；未填時以公司料號代入
        @Size(max = 50)
        String customerItemCode,

        @NotNull
        @Positive
        Long companyItemId
) {
}
