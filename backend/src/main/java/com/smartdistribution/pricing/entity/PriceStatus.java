package com.smartdistribution.pricing.entity;

import java.util.Set;

/**
 * 價格版本狀態，客戶價格與標準售價共用
 * ACTIVE 是同一對象＋單位的最後一個版本；REPLACED 是被後面版本接手的舊版本，截止日之前仍然有效
 * REJECTED、REVOKED 不會再被取用
 */
public enum PriceStatus {
    PENDING_REVIEW,
    ACTIVE,
    REJECTED,
    REPLACED,
    REVOKED;

    /**
     * 核准過、沒有撤銷的版本：取價與排定新版本時只看這兩種
     */
    public static final Set<PriceStatus> APPROVED_VERSIONS = Set.of(ACTIVE, REPLACED);
}
