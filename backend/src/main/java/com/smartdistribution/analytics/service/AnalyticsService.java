package com.smartdistribution.analytics.service;

import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse;

/**
 * 儀表板的分析查詢；每次即時計算，不保存結果
 */
public interface AnalyticsService {

    /**
     * 應收與逾期：金額摘要、帳齡、即將到期與逾期明細，以今天為準
     *
     * @param customerId             只看這個客戶；null 代表全部
     * @param responsibleSalesUserId 只看這位業務負責的 SO 產生的應收；null 代表全部
     * @return 應收分析
     */
    ReceivableAnalysisResponse findReceivableAnalysis(Long customerId, Long responsibleSalesUserId);
}
