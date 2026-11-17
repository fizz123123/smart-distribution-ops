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
     * 找出客戶料號對應在某個單位、某一天適用的價格
     * 目前只有客戶價格，找 ACTIVE 的那一筆；日期等價格效期上線後才會用到
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
     * 引用的價格是否已撤銷；撤銷代表價格本身有誤，引用它的單據不應再被核准
     *
     * @param source 價格來源
     * @param priceId 價格 ID
     * @return 已撤銷時為 true
     */
    boolean isRevoked(PriceSource source, Long priceId);

    /**
     * 引用的價格是否已被新版取代；取代是正常換價，只用來提示
     *
     * @param source 價格來源
     * @param priceId 價格 ID
     * @return 已被取代時為 true
     */
    boolean isReplaced(PriceSource source, Long priceId);

    /**
     * 價格從哪裡來
     */
    enum PriceSource {
        CUSTOMER_PRICE
    }

    /**
     * 取價結果：來源與價格 ID 讓單據之後能追溯用的是哪一筆價格
     */
    record ApplicablePrice(
            PriceSource source,
            Long priceId,
            BigDecimal unitPriceExcludingTax
    ) {
    }
}
