package com.smartdistribution.inventory.service;

import java.math.BigDecimal;

/**
 * 餘量取消核准時，銷售模組告訴庫存模組「這筆 SO 明細取消後還需要多少」，
 * 是 {@link InventoryService#releaseForCancellation} 的輸入
 *
 * <p>只傳「取消後的剩餘需求」而不是「取消量」：先取消未預留的部分，
 * 預留超過剩餘需求時才釋放（Q5），這個判斷交給庫存模組在鎖定後計算
 *
 * @param salesOrderLineId 要調整預留的 SO 明細
 * @param companyItemId    要鎖定的料號
 * @param remainingQty     取消後仍需供應的基準數量（有效訂購量 － 已出貨量）
 */
public record ReservationDemand(
        Long salesOrderLineId,
        Long companyItemId,
        BigDecimal remainingQty
) {
}
