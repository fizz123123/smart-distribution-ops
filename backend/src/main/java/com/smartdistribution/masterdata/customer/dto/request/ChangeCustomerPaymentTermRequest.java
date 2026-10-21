package com.smartdistribution.masterdata.customer.dto.request;

import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ChangeCustomerPaymentTermRequest(
        @NotNull
        PaymentTerm paymentTerm,

        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
