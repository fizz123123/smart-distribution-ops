package com.smartdistribution.analytics.overview.service;

import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse;

import java.time.LocalDate;

/**
 * 營運總覽；只組合四個詳情分析的結果，不另寫算法
 */
public interface OverviewAnalyticsService {

    /**
     * 只有營運主管能看；篩選分別交給各分析，各分析用不到的篩選會被忽略
     *
     * @param from                   本期起日；和迄日一起不帶時是本月至今
     * @param to                     本期迄日
     * @param customerId             接單營收、交易降溫、應收用
     * @param companyItemId          接單營收、缺貨用
     * @param responsibleSalesUserId 接單營收、交易降溫、應收用
     * @return 6 張卡片、近六個月趨勢、3 張待處理清單
     */
    OverviewAnalysisResponse findOverview(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId
    );
}
