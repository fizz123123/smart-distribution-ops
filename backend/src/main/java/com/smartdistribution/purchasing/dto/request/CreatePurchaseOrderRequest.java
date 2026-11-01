package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 草稿只要求供應商；預計到貨日、明細與單價可以之後再補，送審時才檢查是否完整
 */
public record CreatePurchaseOrderRequest(
        @NotNull
        Long supplierId,

        LocalDate expectedArrivalDate,

        @Size(max = 500)
        String remark,

        @NotNull
        @Size(max = 100)
        List<@Valid @NotNull PurchaseOrderLineRequest> lines
) {
}
