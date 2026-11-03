package com.smartdistribution.fulfillment.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * 倉管回報一筆 DO 明細實際找到多少，以 SO 明細的單位表示；0 代表這一筆這次不出
 */
public record ActualQuantityRequest(
        @NotNull
        Long deliveryOrderLineId,

        @NotNull
        @PositiveOrZero
        @Digits(integer = 12, fraction = 3)
        BigDecimal actualQuantity
) {
}
