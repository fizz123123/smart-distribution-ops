package com.smartdistribution.fulfillment.repository;

import com.smartdistribution.fulfillment.entity.DeliveryOrderStatus;

import java.math.BigDecimal;

/**
 * 某筆 SO 明細、某一種 DO 狀態的數量加總（基準單位）
 *
 * @param plannedQty 預計量加總
 * @param actualQty  實際量加總；草稿還沒有實際量，加總會是 null，這裡轉成 0
 */
public record ShipmentQuantityProjection(
        Long salesOrderLineId,
        DeliveryOrderStatus status,
        BigDecimal plannedQty,
        BigDecimal actualQty
) {

    public ShipmentQuantityProjection {
        actualQty = actualQty == null ? BigDecimal.ZERO : actualQty;
    }
}
