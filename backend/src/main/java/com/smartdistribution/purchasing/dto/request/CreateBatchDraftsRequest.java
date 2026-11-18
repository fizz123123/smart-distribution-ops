package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 從缺料清單勾選 SO 明細，依供應商分組，一家供應商產生一張 PO 草稿
 */
public record CreateBatchDraftsRequest(
        @NotEmpty
        @Size(max = 100)
        List<@Valid @NotNull Line> lines
) {

    /**
     * @param supplierId 不填就用料號的偏好供應商
     * @param quantity   庫存基準單位的數量，通常是缺料清單的尚待規劃
     */
    public record Line(
            @NotNull
            Long salesOrderLineId,

            Long supplierId,

            @NotNull
            @Positive
            @Digits(integer = 12, fraction = 3)
            BigDecimal quantity
    ) {
    }
}
