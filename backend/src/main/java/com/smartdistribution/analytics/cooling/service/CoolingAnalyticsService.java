package com.smartdistribution.analytics.cooling.service;

import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse;

import java.time.LocalDate;

/**
 * 客戶交易降溫分析；每次即時計算，不保存結果
 */
public interface CoolingAnalyticsService {

    /**
     * 只有 ANALYTICS_SALES_VIEW 的業務只用自己負責的 SO 計算，指定別人回 403
     *
     * @param asOf                   分析基準日；沒帶時是今天，不能是未來
     * @param from                   補充證據的本期起日；和迄日一起不帶時是本月至今
     * @param to                     補充證據的本期迄日
     * @param customerId             null 代表全部客戶
     * @param responsibleSalesUserId null 代表全部業務
     * @return 有已核准 SO 的客戶，依等級與平均月營收排序
     */
    CoolingAnalysisResponse findCoolingAnalysis(
            LocalDate asOf,
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long responsibleSalesUserId
    );
}
