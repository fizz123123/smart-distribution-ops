package com.smartdistribution.pricing.service;

import com.smartdistribution.pricing.entity.PriceStatus;

import java.time.LocalDate;

/**
 * 價格版本在某一天的效期狀態；只看日期，查詢時算出來，不存進資料庫
 * 和審核狀態分開：ACTIVE 代表「這條版本線最新的已核准版本」，不代表今天有效
 */
public enum PriceEffectiveState {
    UPCOMING,
    EFFECTIVE,
    EXPIRED;

    /**
     * 依日期判斷效期狀態；價格清單的效期篩選用同樣的規則寫在查詢裡
     *
     * @param status 審核狀態；只有核准過、沒撤銷的版本有效期狀態
     * @param effectiveFrom 生效日
     * @param effectiveTo 截止日，null 代表不限期
     * @param today 今天（台北時區）
     * @return 效期狀態；待覆核、已拒絕、已撤銷時為 null
     */
    public static PriceEffectiveState of(
            PriceStatus status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            LocalDate today
    ) {
        if (!PriceStatus.APPROVED_VERSIONS.contains(status)) {
            return null;
        }

        if (effectiveFrom.isAfter(today)) {
            return UPCOMING;
        }

        if (effectiveTo != null && effectiveTo.isBefore(today)) {
            return EXPIRED;
        }

        return EFFECTIVE;
    }
}
