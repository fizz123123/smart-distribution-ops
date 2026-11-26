package com.smartdistribution.pricing.dto.response;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.service.PriceSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 建立 SO 時預填的價格；和送審時取價用同一套規則
 */
public record ApplicablePriceResponse(
        Long customerItemMappingId,
        UnitOfMeasure unit,
        LocalDate date, //取價日期
        PriceOption current, //取價日期適用的價格；沒有價格時為 null
        NextPrice next //之後第一次變動；沒有已排定的變動時為 null
) {

    public record PriceOption(
            PriceSource source,
            Long priceId,
            BigDecimal unitPriceExcludingTax,
            LocalDate effectiveFrom, //這個價格版本的效期
            LocalDate effectiveTo,
            boolean unitPriceEditable //標準售價可以調整單價，客戶價格不行；前端依此決定單價欄位要不要鎖
    ) {
    }

    public record NextPrice(
            LocalDate appliesFrom, //從這天起改用 price
            PriceOption price //null 代表從這天起沒有價格
    ) {
    }
}
