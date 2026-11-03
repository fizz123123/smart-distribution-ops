package com.smartdistribution.inventory.service;

import java.math.BigDecimal;

/**
 * DO 過帳時，出貨模組交給庫存模組的「一筆實際出貨」，
 * 是 {@link InventoryService#consumeForDelivery} 的輸入
 *
 * @param deliveryOrderLineId 出庫異動與預留事件的來源
 * @param companyItemId       要鎖定與扣減的料號
 * @param salesOrderLineId    要使用預留的 SO 明細
 * @param shippedBaseQty      實際出貨量（基準單位），大於 0
 */
public record DeliveryLine(
        Long deliveryOrderLineId,
        Long companyItemId,
        Long salesOrderLineId,
        BigDecimal shippedBaseQty
) {
}
