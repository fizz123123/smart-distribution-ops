package com.smartdistribution.pricing.service;

import com.smartdistribution.masterdata.common.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 價格模組對外的取價服務：其他模組只透過這裡取價，不直接讀價格表
 * 取價規則（找哪一種價格、哪個版本）都集中在價格模組
 */
public interface PriceLookupService {

    /**
     * 找出客戶料號對應在某個單位、某一天適用的價格，順序：
     * 1. 那一天有效的客戶價格
     * 2. 沒有就用那一天有效的標準售價（依對應的公司料號）
     * 兩種都沒有就回傳空的，SO 不能送審
     *
     * @param customerItemMappingId 客戶料號對應 ID，已經決定了客戶與公司料號
     * @param unit 計價單位，也就是訂購單位
     * @param priceDate 取價日期
     * @return 適用的價格；沒有可用價格時為空
     */
    Optional<ApplicablePrice> findApplicablePrice(
            Long customerItemMappingId,
            UnitOfMeasure unit,
            LocalDate priceDate
    );

    /**
     * 取價日期之後，適用的價格第一次改變的日期，以及改成哪個價格；規則和 findApplicablePrice 相同
     * 可能改變的時間點：之後才開始的客戶價格、標準售價版本，以及目前價格到期的隔天
     *
     * @param customerItemMappingId 客戶料號對應 ID
     * @param unit 計價單位，也就是訂購單位
     * @param priceDate 取價日期
     * @return 下一次變動；沒有已排定的變動時為空
     */
    Optional<PriceChange> findNextChange(Long customerItemMappingId, UnitOfMeasure unit, LocalDate priceDate);

    /**
     * 引用的價格是否已撤銷；撤銷代表價格本身有誤，引用它的單據不應再被核准
     *
     * @param source 價格來源
     * @param priceId 價格 ID
     * @return 已撤銷時為 true
     */
    boolean isRevoked(PriceSource source, Long priceId);

    /**
     * 取價結果：來源與價格 ID 讓單據之後能追溯用的是哪一筆價格
     */
    record ApplicablePrice(
            PriceSource source,
            Long priceId,
            BigDecimal unitPriceExcludingTax,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {

        //同一個來源的同一筆價格；比較單價不夠，兩個版本可能剛好同價
        public boolean isSamePrice(ApplicablePrice other) {
            return other != null && source == other.source && priceId.equals(other.priceId);
        }
    }

    /**
     * 價格變動：從 appliesFrom 起改用 price；price 為 null 代表從那天起沒有價格
     */
    record PriceChange(
            LocalDate appliesFrom,
            ApplicablePrice price
    ) {
    }
}
