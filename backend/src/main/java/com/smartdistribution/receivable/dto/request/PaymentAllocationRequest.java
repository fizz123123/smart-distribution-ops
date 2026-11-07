package com.smartdistribution.receivable.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

//這筆收款要核銷哪一筆應收、核銷多少；應收都是整數元，所以金額也只接受整數
public record PaymentAllocationRequest(
        @NotNull
        Long receivableId,

        @NotNull
        @Positive
        @Digits(integer = 13, fraction = 0)
        BigDecimal allocatedAmount
) {
}
