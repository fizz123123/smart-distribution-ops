package com.smartdistribution.inventory.entity;

/**
 * 代表：這筆庫存變動是哪一種、來源在哪張表
 * 實體庫存異動類型；類型決定 source_id 指向哪一張資料表
 */
public enum InventoryTransactionType {

    // source_id → inventory_adjustment
    OPENING_BALANCE,
    // source_id → inventory_adjustment
    ADJUSTMENT,
    // source_id → inventory_adjustment（反向更正的那一張）
    ADJUSTMENT_REVERSAL
}
