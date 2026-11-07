package com.smartdistribution.sales.service;

import java.math.BigDecimal;

/**
 * 一筆 SO 明細的出貨數量，以庫存基準單位表示
 *
 * @param shippedQty    已出貨：已過帳 DO 的實際出貨量
 * @param deliveringQty 進行中 DO：草稿算預計量、完成揀貨算實際量；這部分已排入出貨，不能取消
 */
public record ShipmentQuantity(
        BigDecimal shippedQty,
        BigDecimal deliveringQty
) {

    public static final ShipmentQuantity NONE = new ShipmentQuantity(BigDecimal.ZERO, BigDecimal.ZERO);
}
