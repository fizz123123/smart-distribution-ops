package com.smartdistribution.analytics.shortage.repository.projection;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;

/**
 * 一個料號的庫存、安全庫存、近 30 天出貨與平均採購週期，數量都是庫存基準單位
 *
 * @param active             料號是否啟用；停用的料號不判斷庫存面的風險
 * @param safetyStockQty     採購主管設定的安全庫存
 * @param shippedQty         近 30 天（含今天）已過帳 DO 的實際出貨量
 * @param averageLeadTimeDays 最近六個月收齊的 PO 明細，從核准到最後一次合格收貨的平均天數；沒有資料時是 null
 */
public record ItemStockProjection(
        Long companyItemId,
        String itemCode,
        String itemName,
        UnitOfMeasure baseUnit,
        boolean active,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        BigDecimal safetyStockQty,
        BigDecimal shippedQty,
        BigDecimal averageLeadTimeDays
) {

    //可用量 ＝ 實體數量 － 已預留
    public BigDecimal availableQty() {
        return onHandQty.subtract(reservedQty);
    }
}
