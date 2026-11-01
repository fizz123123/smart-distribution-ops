package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 整份送出：可為空的欄位以 null 表示清空，明細整批取代
 */
public record UpdatePurchaseOrderRequest(
        @NotNull
        Long supplierId,

        LocalDate expectedArrivalDate,

        @Size(max = 500)
        String remark,

        @NotNull
        @Size(max = 100)
        List<@Valid @NotNull PurchaseOrderLineRequest> lines,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
