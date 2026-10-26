package com.smartdistribution.inventory.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;

public record InventoryBalanceResponse(
        Long companyItemId,
        String itemCode,
        String name,
        UnitOfMeasure baseUnit,

        //可出貨實體數量
        BigDecimal onHandQty,

        //有效預留總量
        BigDecimal reservedQty,

        //可用庫存 ＝ 可出貨實體數量 － 有效預留總量
        BigDecimal availableQty,
        BigDecimal safetyStockBaseQty
) {
}
