package com.smartdistribution.purchasing.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(
        @NotNull
        Long companyItemId,

        //有值就是關聯這筆 SO 明細的缺料採購；不填就是一般補庫
        Long salesOrderLineId,

        //實際小數位再依單位檢查，例如「支」不允許小數
        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 3)
        BigDecimal quantity,

        //庫存基準單位，或公司料號已設定換算的單位
        @NotNull
        UnitOfMeasure unit,

        //以訂購單位計的未稅單價；草稿可以先不填，送審時必填
        @Positive
        @Digits(integer = 13, fraction = 2)
        BigDecimal unitPriceExcludingTax
) {
}
