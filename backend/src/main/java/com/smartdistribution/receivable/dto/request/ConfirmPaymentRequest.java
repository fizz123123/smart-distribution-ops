package com.smartdistribution.receivable.dto.request;

import com.smartdistribution.receivable.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 登錄收款並核銷：所有應收都要屬於這個客戶，分配加總必須等於收款金額
 * 不接受預收、溢收或未分配款
 */
public record ConfirmPaymentRequest(
        //前端每次收款操作產生一個；同一個值重送時回傳原本那筆收款
        @NotBlank
        @Size(max = 64)
        String clientRequestId,

        @NotNull
        Long customerId,

        @NotNull
        LocalDate receivedDate,

        @NotNull
        @Positive
        @Digits(integer = 13, fraction = 0)
        BigDecimal amount,

        @NotNull
        PaymentMethod method,

        @Size(max = 50)
        String referenceNumber,

        @NotEmpty
        @Size(max = 100)
        List<@Valid @NotNull PaymentAllocationRequest> allocations
) {
}
