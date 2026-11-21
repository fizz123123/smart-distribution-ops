package com.smartdistribution.analytics.repository;

/**
 * 分析查詢共用的篩選；欄位是 null 代表不篩選
 *
 * @param responsibleSalesUserId SO 的負責業務；營收、應收、出貨都透過 SO 對回負責業務
 */
public record AnalyticsFilter(
        Long customerId,
        Long companyItemId,
        Long responsibleSalesUserId
) {
}
