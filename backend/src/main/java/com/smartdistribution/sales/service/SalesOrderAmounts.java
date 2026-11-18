package com.smartdistribution.sales.service;

import com.smartdistribution.sales.entity.SalesOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * SO 的數量與金額計算，規則集中在這裡：
 * - 基準數量 ＝ 訂購數量 × 倍率，保留 3 位小數
 * - 明細小計 ＝ 計價數量 × 未稅單價，保留 2 位小數
 * - 表頭未稅 ＝ 明細小計加總，四捨五入到元
 * - 稅額 ＝ 表頭未稅 × 5%，四捨五入到元
 * - 含稅 ＝ 表頭未稅 ＋ 稅額
 * - 有效小計 ＝ （訂購數量 － 已核准取消數量）× 成交價，表頭有效金額用同一套規則加總
 * 一律 HALF_UP
 */
public final class SalesOrderAmounts {

    public static final BigDecimal TAX_RATE = new BigDecimal("0.0500");

    private SalesOrderAmounts() {
    }

    public static BigDecimal baseQty(BigDecimal quantity, BigDecimal conversionFactor) {
        return quantity.multiply(conversionFactor).setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineAmount(BigDecimal pricingQty, BigDecimal unitPriceExcludingTax) {
        return pricingQty.multiply(unitPriceExcludingTax).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 扣除已核准取消後的小計；原始小計是送審快照，不會改
     *
     * @param quantity              訂購數量
     * @param cancelledQuantity     已核准取消的數量，和訂購數量同單位
     * @param unitPriceExcludingTax 快照的成交價
     * @return 有效小計
     */
    public static BigDecimal effectiveLineAmount(
            BigDecimal quantity,
            BigDecimal cancelledQuantity,
            BigDecimal unitPriceExcludingTax
    ) {
        return lineAmount(quantity.subtract(cancelledQuantity), unitPriceExcludingTax);
    }

    public static SalesOrder.Amounts documentAmounts(List<BigDecimal> lineAmounts) {
        BigDecimal untaxed = lineAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal tax = untaxed.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);

        return new SalesOrder.Amounts(untaxed, tax, untaxed.add(tax));
    }
}
