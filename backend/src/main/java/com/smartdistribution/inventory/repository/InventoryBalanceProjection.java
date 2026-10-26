package com.smartdistribution.inventory.repository;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;

/**
 * 庫存餘額清單的查詢結果，料號與品名從主檔唯讀取得
 */
public record InventoryBalanceProjection(
        Long companyItemId,
        String itemCode,
        String name,
        UnitOfMeasure baseUnit,
        BigDecimal onHandQty,
        BigDecimal safetyStockBaseQty
) {
}
