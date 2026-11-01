package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 數量都以 PO 明細的單位表示；實收 ＝ 合格 ＋ 損壞 ＋ 規格不符
 */
public record GoodsReceiptLineRequest(
        @NotNull
        Long purchaseOrderLineId,

        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 3)
        BigDecimal receivedQuantity,

        @NotNull
        @PositiveOrZero
        @Digits(integer = 12, fraction = 3)
        BigDecimal acceptedQuantity,

        @NotNull
        @PositiveOrZero
        @Digits(integer = 12, fraction = 3)
        BigDecimal damagedQuantity,

        @NotNull
        @PositiveOrZero
        @Digits(integer = 12, fraction = 3)
        BigDecimal nonconformingQuantity,

        //有損壞或規格不符時必填
        @Size(max = 500)
        String exceptionReason
) {
}
