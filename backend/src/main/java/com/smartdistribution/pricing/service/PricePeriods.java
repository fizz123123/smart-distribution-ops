package com.smartdistribution.pricing.service;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;

import java.time.LocalDate;

/**
 * 價格有效期間的共用規則：價格不溯及既往，生效日最早是核准當天
 * 今天由呼叫端用注入的 Clock 取得後傳進來，這裡只放規則，不取時間
 */
public final class PricePeriods {

    private PricePeriods() {
    }

    /**
     * 建立價格時檢查申請的期間：生效日不能早於今天，截止日不能早於生效日
     *
     * @param effectiveFrom 申請的生效日，null 代表核准當天
     * @param effectiveTo 截止日，null 代表不限期
     * @param today 今天（台北時區）
     */
    public static void validateRequested(LocalDate effectiveFrom, LocalDate effectiveTo, LocalDate today) {
        if (effectiveFrom != null && effectiveFrom.isBefore(today)) {
            throw new BusinessException(ErrorCode.PRICE_PERIOD_INVALID);
        }

        ensureNotEnded(resolveEffectiveFrom(effectiveFrom, today), effectiveTo);
    }

    /**
     * 實際生效日：沒填，或核准時已經過了申請的生效日，都改成今天
     *
     * @param requestedFrom 申請的生效日
     * @param today 今天（台北時區）
     * @return 實際生效日
     */
    public static LocalDate resolveEffectiveFrom(LocalDate requestedFrom, LocalDate today) {
        return requestedFrom == null || requestedFrom.isBefore(today) ? today : requestedFrom;
    }

    /**
     * 新版本只能排在最後一個版本之後：生效日必須晚於最後一個版本的生效日，同一天也不行
     * 同一條版本線每天最多一個價格，前一版被截短後截止日一定不早於自己的生效日
     *
     * @param latestFrom 最後一個版本的生效日，沒有任何版本時為 null
     * @param effectiveFrom 新版本的生效日
     */
    public static void ensureAfterLatest(LocalDate latestFrom, LocalDate effectiveFrom) {
        if (latestFrom != null && !effectiveFrom.isAfter(latestFrom)) {
            throw new BusinessException(
                    ErrorCode.PRICE_PERIOD_CONFLICT,
                    "已有 " + latestFrom + " 起的價格，新價格的生效日必須晚於這一天；"
                            + "價格有誤請先撤銷再重新建立，只是調價請把生效日設在之後。"
            );
        }
    }

    /**
     * 版本在某一天是否還有效；用來判斷最後一個版本會不會被新版本截短
     *
     * @param effectiveTo 版本的截止日，null 代表不限期
     * @param date 要判斷的日期
     * @return 那一天仍有效時為 true
     */
    public static boolean stillEffectiveOn(LocalDate effectiveTo, LocalDate date) {
        return effectiveTo == null || !effectiveTo.isBefore(date);
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
