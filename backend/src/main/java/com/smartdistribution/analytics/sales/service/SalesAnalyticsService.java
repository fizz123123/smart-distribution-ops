package com.smartdistribution.analytics.sales.service;

import com.smartdistribution.analytics.sales.dto.response.CancellationSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.NewOrderSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.RevenueSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

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

    /**
     * 營收的來源：期間內出貨的應收明細，日期由新到舊；篩選與權限和 findSalesAnalysis 相同
     * 前期的來源，就把前期的起訖日當作 from、to 傳進來
     *
     * @return 一頁應收明細，加起來等於同樣條件下的營收
     */
    PageResponse<RevenueSourceResponse> findRevenueSources(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            Pageable pageable
    );

    /**
     * @return 一頁期間內核准的 SO，加起來等於同樣條件下的新增核准接單額
     */
    PageResponse<NewOrderSourceResponse> findNewOrderSources(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            Pageable pageable
    );

    /**
     * @return 一頁期間內核准的取消申請明細，加起來等於同樣條件下的核准取消額
     */
    PageResponse<CancellationSourceResponse> findCancellationSources(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            Pageable pageable
    );
}
