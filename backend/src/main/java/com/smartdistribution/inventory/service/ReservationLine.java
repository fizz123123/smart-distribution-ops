package com.smartdistribution.inventory.service;

import java.math.BigDecimal;

/**
 * SO 核准時，銷售模組交給庫存模組的「一筆明細要預留多少」，
 * 是 {@link InventoryService#reserveForSalesOrder} 的輸入
 *
 * <p>銷售模組本來就會呼叫庫存模組，如果庫存再依賴銷售的 entity，
 * 兩個模組就互相依賴，改由銷售模組轉成這個 record，
 * 庫存只拿到預留需要的 5 個值，也不會不小心用到 SO 的其他欄位或關聯
 *
 * <p>同一料號有多筆明細時，庫存依清單順序分配可用量，所以呼叫端要依行號排序
 *
 * @param salesOrderLineId 預留屬於哪一筆 SO 明細；每筆明細最多一筆預留
 * @param salesOrderId     存在預留上，供查詢與追溯
 * @param salesOrderNumber SO 編號快照，讓庫存畫面與錯誤訊息不必回頭查銷售模組
 * @param companyItemId    要鎖定與計算可用量的料號
 * @param baseQty          要求預留的數量，以庫存基準單位表示
 */
public record ReservationLine(
        Long salesOrderLineId,
        Long salesOrderId,
        String salesOrderNumber,
        Long companyItemId,
        BigDecimal baseQty
) {
}
