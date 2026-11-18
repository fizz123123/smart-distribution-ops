package com.smartdistribution.sales.dto.request;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesOrderLineRequest(
        @NotNull
        Long customerItemMappingId,

        //實際小數位再依單位檢查，例如 "支" 不允許小數
        @NotNull
        @Positive
        @Digits(integer = 12, fraction = 3)
        BigDecimal quantity,

        //必須是訂單日期有有效價格的單位，送審時才檢查
        @NotNull
        UnitOfMeasure unit,

        //業務議定的未稅單價；留空就用底價，送審時不能低於底價
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 13, fraction = 2)
        BigDecimal requestedUnitPrice,

        //這一行自己的交期；留空就沿用表頭的要求交期
        LocalDate requestedDeliveryDate
) {

    /**
     * 實際交期：明細有填就用明細的，沒填沿用表頭；規則和 SalesOrderLine.resolveDeliveryDate 相同
     *
     * @param headerDeliveryDate 表頭的要求交期，可能為 null
     * @return 實際交期，兩者都沒填時為 null
     */
    public LocalDate resolveDeliveryDate(LocalDate headerDeliveryDate) {
        return requestedDeliveryDate != null ? requestedDeliveryDate : headerDeliveryDate;
    }
}
