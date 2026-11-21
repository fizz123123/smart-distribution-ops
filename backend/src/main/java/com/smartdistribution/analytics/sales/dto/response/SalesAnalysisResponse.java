package com.smartdistribution.analytics.sales.dto.response;

import com.smartdistribution.analytics.common.dto.response.AnalyticsPeriodResponse;
import com.smartdistribution.analytics.common.dto.response.MetricComparison;
import com.smartdistribution.analytics.common.service.AnalyticsNote;
import com.smartdistribution.analytics.common.service.SupplyStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 接單與營收；金額都是未稅，分成四個區塊，前端一個區塊對應一個物件
 *
 * @param comparison    本期與前期的比較
 * @param trend         近六個月，由舊到新；不受期間影響
 * @param contributions 客戶與料號的營收增減前三名
 * @param backlog       已接單未出貨，查詢當下的狀態，不受期間影響
 */
public record SalesAnalysisResponse(
        AnalyticsPeriodResponse period,
        Comparison comparison,
        List<MonthlyTrend> trend,
        Contributions contributions,
        Backlog backlog
) {

    /**
     * @param netOrderAmount     新增核准接單額 － 核准取消額
     * @param averageOrderAmount 新增核准接單額 ÷ SO 張數；沒有 SO 時算不出來
     */
    public record Comparison(
            MetricComparison revenue,
            MetricComparison newOrderAmount,
            MetricComparison cancellationAmount,
            MetricComparison netOrderAmount,
            MetricComparison newOrderCount,
            MetricComparison averageOrderAmount,
            MetricComparison activeCustomerCount,
            MetricComparison orderingCustomerCount,
            OnTimeDelivery onTimeDelivery
    ) {
    }

    public record OnTimeDelivery(
            OnTimeStats current,
            OnTimeStats previous
    ) {
    }

    /**
     * @param rate 準時的比例，0.95 代表 95%；期間內沒有出貨時為 null
     */
    public record OnTimeStats(
            long shippedLineCount,
            long onTimeLineCount,
            BigDecimal rate,
            AnalyticsNote note
    ) {
    }

    /**
     * @param month   年月，例如 2026-11
     * @param partial 本月還沒過完，數字是截至今天的累計
     */
    public record MonthlyTrend(
            String month,
            BigDecimal newOrderAmount,
            BigDecimal cancellationAmount,
            BigDecimal netOrderAmount,
            BigDecimal revenue,
            boolean partial
    ) {
    }

    /**
     * 增加最多、減少最多各取前三；變化為 0 的不列
     */
    public record Contributions(
            List<Contribution> customerIncreases,
            List<Contribution> customerDecreases,
            List<Contribution> itemIncreases,
            List<Contribution> itemDecreases
    ) {
    }

    /**
     * @param id   客戶 ID 或公司料號 ID
     * @param code 客戶代碼或料號
     */
    public record Contribution(
            Long id,
            String code,
            String name,
            BigDecimal currentAmount,
            BigDecimal previousAmount,
            BigDecimal change
    ) {
    }

    /**
     * @param overdueAmount        交期早於今天、還沒出貨的部分
     * @param shortageImpactAmount 缺料影響：供應狀態是缺料待處理或採購延遲風險的部分
     * @param bySupplyStatus       依供應狀態拆解，六個狀態都會列出；加起來等於 amount
     */
    public record Backlog(
            BigDecimal amount,
            int lineCount,
            BigDecimal overdueAmount,
            int overdueLineCount,
            BigDecimal shortageImpactAmount,
            int shortageImpactLineCount,
            List<SupplyStatusAmount> bySupplyStatus
    ) {
    }

    public record SupplyStatusAmount(
            SupplyStatus status,
            BigDecimal amount,
            int lineCount
    ) {
    }
}
