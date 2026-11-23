package com.smartdistribution.analytics.shortage.service;

import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse;

/**
 * 缺貨與採購分析；每次即時計算，不保存結果
 */
public interface ShortageAnalyticsService {

    /**
     * 料號的缺貨風險：庫存面看可用量、安全庫存與再訂購點，供應面看已核准 SO 明細能不能如期備齊
     * 另外附上最近六個月的供應商表現；以今天為準，不受期間影響；不含任何價格
     *
     * @param companyItemId 只看這個料號，供應商表現也只算這個料號的 PO；null 代表全部
     * @return 有風險的料號依最早交期排序，供應商依準時到貨率由低到高
     */
    ShortageAnalysisResponse findShortageAnalysis(Long companyItemId);
}
