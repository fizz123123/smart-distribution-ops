package com.smartdistribution.analytics.sales.service;

import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse;

import java.time.LocalDate;

/**
 * 接單與營收分析；每次即時計算，不保存結果
 */
public interface SalesAnalyticsService {

    /**
     * 只有 ANALYTICS_SALES_VIEW 的業務只看自己負責的 SO，指定別人回 403
     *
     * @param from                   本期起日；和迄日一起不帶時是本月至今
     * @param to                     本期迄日
     * @param customerId             null 代表全部客戶
     * @param companyItemId          null 代表全部料號
     * @param responsibleSalesUserId null 代表全部業務
     * @return 本期與前期比較、近六個月趨勢、增減貢獻、已接單未出貨
     */
    SalesAnalysisResponse findSalesAnalysis(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId
    );
}
