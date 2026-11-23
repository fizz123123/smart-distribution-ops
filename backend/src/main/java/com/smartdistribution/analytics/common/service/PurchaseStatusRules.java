package com.smartdistribution.analytics.common.service;

import java.util.Set;

/**
 * PO 狀態的分類，供應狀態與缺貨分析共用；狀態用字串，和 LinkedPurchaseOrder 一樣不依賴採購模組的列舉
 * ShortageAnalyticsRepository 的 SQL 也用同樣的分類，改這裡時要一起改
 */
public final class PurchaseStatusRules {

    //已核准、還沒收齊：計入採購在途
    private static final Set<String> APPROVED = Set.of("APPROVED", "PARTIALLY_RECEIVED");

    //還沒核准：計入已規劃未下單
    private static final Set<String> NOT_APPROVED = Set.of("DRAFT", "PENDING_APPROVAL", "RETURNED");

    private static final String OPEN_LINE = "OPEN";

    private PurchaseStatusRules() {
    }

    /**
     * @param poStatus PO 表頭狀態
     * @return 已核准還沒收齊；這種 PO 才算採購在途，也才會「到貨延遲」
     */
    public static boolean isApproved(String poStatus) {
        return APPROVED.contains(poStatus);
    }

    /**
     * @param poStatus   PO 表頭狀態
     * @param lineStatus PO 明細狀態
     * @return 已核准，而且這一行還在等貨
     */
    public static boolean isApprovedAndOpen(String poStatus, String lineStatus) {
        return isApproved(poStatus) && OPEN_LINE.equals(lineStatus);
    }

    /**
     * @param poStatus   PO 表頭狀態
     * @param lineStatus PO 明細狀態
     * @return 這一行還沒結：未核准，或已核准還在等貨；已取消、已結案的只是歷史紀錄
     */
    public static boolean isOpen(String poStatus, String lineStatus) {
        return (isApproved(poStatus) || NOT_APPROVED.contains(poStatus)) && OPEN_LINE.equals(lineStatus);
    }
}
