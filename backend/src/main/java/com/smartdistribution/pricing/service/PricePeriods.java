package com.smartdistribution.pricing.service;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 價格有效期間的共用規則：價格不溯及既往，生效日最早是核准當天
 */
public final class PricePeriods {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private PricePeriods() {
    }

    /**
     * 今天（台北時區）；價格的生效、過期都以台北的日期判斷
     *
     * @return 今天
     */
    public static LocalDate today() {
        return LocalDate.now(TAIPEI);
    }

    /**
     * 建立價格時檢查申請的期間：生效日不能早於今天，截止日不能早於生效日
     *
     * @param effectiveFrom 申請的生效日，null 代表核准當天
     * @param effectiveTo 截止日，null 代表不限期
     */
    public static void validateRequested(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveFrom.isBefore(today())) {
            throw new BusinessException(ErrorCode.PRICE_PERIOD_INVALID);
        }

        ensureNotEnded(resolveEffectiveFrom(effectiveFrom), effectiveTo);
    }

    /**
     * 實際生效日：沒填，或核准時已經過了申請的生效日，都改成今天
     *
     * @param requestedFrom 申請的生效日
     * @return 實際生效日
     */
    public static LocalDate resolveEffectiveFrom(LocalDate requestedFrom) {
        LocalDate today = today();

        return requestedFrom == null || requestedFrom.isBefore(today) ? today : requestedFrom;
    }

    /**
     * 截止日不能早於生效日；核准拖太久、截止日已經過了也會在這裡擋下
     *
     * @param effectiveFrom 實際生效日
     * @param effectiveTo 截止日，null 代表不限期
     */
    public static void ensureNotEnded(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(ErrorCode.PRICE_PERIOD_INVALID);
        }
    }
}
