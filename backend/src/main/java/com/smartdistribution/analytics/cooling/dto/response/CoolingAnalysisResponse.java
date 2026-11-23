package com.smartdistribution.analytics.cooling.dto.response;

import com.smartdistribution.analytics.common.dto.response.AnalyticsPeriodResponse;
import com.smartdistribution.analytics.common.dto.response.MetricComparison;
import com.smartdistribution.analytics.common.service.AnalyticsNote;
import com.smartdistribution.analytics.cooling.service.CoolingLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 客戶交易降溫：週期與等級依 asOf 計算；補充證據用儀表板的本期與前期
 *
 * @param asOf      分析基準日，預設今天
 * @param period    補充證據的本期與前期
 * @param customers 有已核准 SO 的客戶；依等級（HIGH、MEDIUM、未達警示、資料不足）排序，同一等級依平均月營收由高到低
 */
public record CoolingAnalysisResponse(
        LocalDate asOf,
        AnalyticsPeriodResponse period,
        List<CustomerCooling> customers
) {

    /**
     * @param active                停用的客戶不評等級
     * @param level                 風險等級；未達警示、資料不足或客戶停用時是 null
     * @param note                  交易日不足 3 個時是 INSUFFICIENT_DATA
     * @param lastTradeDate         最後一張已核准 SO 的訂單日期
     * @param daysSinceLastTrade    asOf － 最後交易日
     * @param tradeDayCount         不重複的訂單日期個數，同一天多張只算一次
     * @param averageCycleDays      平均交易週期（天）
     * @param cycleRatio            距上次交易天數 ÷ 平均交易週期
     * @param averageMonthlyRevenue asOf 往前六個月的營收 ÷ 6，只影響排序
     */
    public record CustomerCooling(
            Long customerId,
            String customerCode,
            String customerName,
            boolean active,
            CoolingLevel level,
            AnalyticsNote note,
            LocalDate lastTradeDate,
            long daysSinceLastTrade,
            int approvedOrderCount,
            int tradeDayCount,
            BigDecimal averageCycleDays,
            BigDecimal cycleRatio,
            BigDecimal averageMonthlyRevenue,
            Evidence evidence
    ) {
    }

    /**
     * 補充證據，不改變風險等級；和接單營收分析篩這個客戶時是同樣的算法
     *
     * @param orderCount            本期與前期新增核准的 SO 張數（依核准日）
     * @param orderAmount           本期與前期新增核准的接單額
     * @param revenue               本期與前期的營收（依實際出貨日）
     * @param shortageLineCount     目前還有尚待規劃的 SO 明細數，以今天為準
     * @param cancellationCount     本期核准的取消申請張數
     * @param lateShipmentLineCount 本期實際出貨日晚於交期的 DO 明細數
     */
    public record Evidence(
            MetricComparison orderCount,
            MetricComparison orderAmount,
            MetricComparison revenue,
            int shortageLineCount,
            int cancellationCount,
            int lateShipmentLineCount
    ) {
    }
}
