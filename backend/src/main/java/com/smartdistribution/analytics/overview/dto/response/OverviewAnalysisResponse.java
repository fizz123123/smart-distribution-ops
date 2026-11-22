package com.smartdistribution.analytics.overview.dto.response;

import com.smartdistribution.analytics.common.dto.response.AnalyticsPeriodResponse;
import com.smartdistribution.analytics.common.dto.response.MetricComparison;
import com.smartdistribution.analytics.cooling.service.CoolingLevel;
import com.smartdistribution.analytics.receivable.dto.response.ReceivableAnalysisResponse.DueReceivable;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.MonthlyTrend;
import com.smartdistribution.analytics.shortage.service.ShortageRisk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 營運總覽：回答「現在哪裡有問題」；6 張卡片、近六個月趨勢、3 張待處理清單
 * 每個數字都直接取自對應的詳情分析，所以總覽和詳情一定相同
 *
 * @param asOf  狀態型指標（未出貨、缺料、降溫、應收）的基準日，也就是今天
 * @param trend 近六個月的接單與營收，和接單營收分析的趨勢相同
 */
public record OverviewAnalysisResponse(
        AnalyticsPeriodResponse period,
        LocalDate asOf,
        Cards cards,
        List<MonthlyTrend> trend,
        PendingLists pending
) {

    /**
     * @param revenue            卡片 1：本期營收與前期比較
     * @param netOrderAmount     卡片 2：本期淨接單額與前期比較
     * @param backlog            卡片 3：已接單未出貨
     * @param shortageImpact     卡片 4：缺料影響金額
     * @param cooling            卡片 5：交易降溫客戶
     * @param overdueReceivables 卡片 6：逾期應收
     */
    public record Cards(
            MetricComparison revenue,
            MetricComparison netOrderAmount,
            BacklogCard backlog,
            ShortageImpactCard shortageImpact,
            CoolingCard cooling,
            OverdueReceivableCard overdueReceivables
    ) {
    }

    /**
     * @param overdueAmount 副標：其中已逾交期的金額
     */
    public record BacklogCard(
            BigDecimal amount,
            BigDecimal overdueAmount
    ) {
    }

    /**
     * @param amount    缺料待處理與採購延遲風險的未出貨金額
     * @param itemCount 副標：涉及幾個料號
     */
    public record ShortageImpactCard(
            BigDecimal amount,
            int itemCount
    ) {
    }

    /**
     * @param customerCount HIGH 加 MEDIUM 的客戶數
     * @param highCount     副標：其中高風險幾位
     */
    public record CoolingCard(
            int customerCount,
            int highCount
    ) {
    }

    /**
     * @param outstandingAmount 逾期應收的未收金額，包含還沒確認的草稿
     * @param unconfirmedCount  副標：其中還沒確認的筆數
     */
    public record OverdueReceivableCard(
            BigDecimal outstandingAmount,
            int count,
            int unconfirmedCount
    ) {
    }

    /**
     * 各取前 10 筆，附總筆數；完整清單在各詳情
     *
     * @param shortages        有風險的料號，依最早交期
     * @param coolingCustomers HIGH、MEDIUM 的客戶，依等級與平均月營收
     * @param dueReceivables   即將到期與逾期的應收，依到期日
     */
    public record PendingLists(
            TopList<ShortageItem> shortages,
            TopList<CoolingCustomer> coolingCustomers,
            TopList<DueReceivable> dueReceivables
    ) {
    }

    /**
     * @param totalCount 符合條件的總筆數，items 最多 10 筆
     */
    public record TopList<T>(
            int totalCount,
            List<T> items
    ) {
    }

    /**
     * @param affectedLineCount 受影響的 SO 明細數
     */
    public record ShortageItem(
            Long companyItemId,
            String itemCode,
            String itemName,
            List<ShortageRisk> stockRisks,
            List<ShortageRisk> supplyRisks,
            LocalDate earliestDeliveryDate,
            int affectedLineCount
    ) {
    }

    public record CoolingCustomer(
            Long customerId,
            String customerCode,
            String customerName,
            CoolingLevel level,
            long daysSinceLastTrade,
            BigDecimal averageCycleDays,
            BigDecimal averageMonthlyRevenue
    ) {
    }
}
