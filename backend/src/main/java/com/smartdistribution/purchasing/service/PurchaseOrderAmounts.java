package com.smartdistribution.purchasing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * PO 的數量與金額計算，捨入規則和 SO 相同，一律 HALF_UP：
 * - 基準數量 ＝ 訂購數量 × 倍率，保留 3 位小數
 * - 明細小計 ＝ 訂購數量 × 未稅單價，保留 2 位小數
 * - 未稅總額 ＝ 明細小計加總，四捨五入到元
 * - 稅額 ＝ 未稅總額 × 5%，四捨五入到元；只顯示在 PDF，不保存
 */
public final class PurchaseOrderAmounts {

    public static final BigDecimal TAX_RATE = new BigDecimal("0.05");

    private PurchaseOrderAmounts() {
    }

    public static BigDecimal baseQty(BigDecimal quantity, BigDecimal conversionFactor) {
        return quantity.multiply(conversionFactor).setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineAmount(BigDecimal quantity, BigDecimal unitPriceExcludingTax) {
        return quantity.multiply(unitPriceExcludingTax).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal untaxedTotal(List<BigDecimal> lineAmounts) {
        return lineAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal tax(BigDecimal untaxedTotal) {
        return untaxedTotal.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);
    }
}
