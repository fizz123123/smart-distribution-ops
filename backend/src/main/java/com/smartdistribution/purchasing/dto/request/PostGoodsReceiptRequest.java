package com.smartdistribution.purchasing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 過帳收貨：建立即過帳，送出前請在畫面上確認數量
 */
public record PostGoodsReceiptRequest(
        @NotNull
        LocalDate receivedDate,

        @NotEmpty
        @Size(max = 100)
        List<@Valid @NotNull GoodsReceiptLineRequest> lines
) {
}
