package com.smartdistribution.analytics.sales.mapper;

import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.Backlog;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.Comparison;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.Contribution;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.Contributions;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.MonthlyTrend;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.OnTimeDelivery;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.OnTimeStats;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.SupplyStatusAmount;
import com.smartdistribution.analytics.sales.repository.projection.AmountByKeyProjection;
import com.smartdistribution.analytics.sales.repository.projection.BacklogLineProjection;
import com.smartdistribution.analytics.sales.repository.projection.ContributionProjection;
import com.smartdistribution.analytics.sales.repository.projection.NewOrderTotalsProjection;
import com.smartdistribution.analytics.sales.repository.projection.OnTimeDeliveryProjection;
import com.smartdistribution.analytics.sales.repository.projection.RevenueTotalsProjection;
import com.smartdistribution.analytics.common.service.AnalyticsNote;
import com.smartdistribution.analytics.common.service.AnalyticsRules;
import com.smartdistribution.analytics.common.service.MetricChanges;
import com.smartdistribution.analytics.common.service.SupplyStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把接單與營收的查詢結果組成回應的四個區塊：本期與前期比較、近六個月趨勢、增減貢獻、已接單未出貨
 */
@Component
public class SalesAnalysisMapper {

    private static final int TOP_CONTRIBUTIONS = 3;

    /**
     * @param current  本期的四組合計
     * @param previous 前期的四組合計
     * @return 比較值；前期是 0 時不算變化率
     */
    public Comparison toComparison(PeriodTotals current, PeriodTotals previous) {
        return new Comparison(
                MetricChanges.compare(current.revenue().revenue(), previous.revenue().revenue()),
                MetricChanges.compare(current.newOrders().amount(), previous.newOrders().amount()),
                MetricChanges.compare(current.cancellationAmount(), previous.cancellationAmount()),
                MetricChanges.compare(current.netOrderAmount(), previous.netOrderAmount()),
                MetricChanges.compare(current.newOrders().orderCount(), previous.newOrders().orderCount()),
                MetricChanges.compare(current.averageOrderAmount(), previous.averageOrderAmount()),
                MetricChanges.compare(
                        current.revenue().activeCustomerCount(),
                        previous.revenue().activeCustomerCount()
                ),
                MetricChanges.compare(current.newOrders().customerCount(), previous.newOrders().customerCount()),
                new OnTimeDelivery(toOnTimeStats(current.onTime()), toOnTimeStats(previous.onTime()))
        );
    }

    /**
     * 查詢結果只有有資料的月份，這裡補成固定六個月，沒有資料的月份是 0
     *
     * @param months         近六個月，由舊到新
     * @param today          今天，用來標示本月還沒過完
     * @param newOrders      每月新增核准接單額
     * @param cancellations  每月核准取消額
     * @param revenue        每月營收
     * @return 六個月的趨勢
     */
    public List<MonthlyTrend> toTrend(
            List<YearMonth> months,
            LocalDate today,
            List<AmountByKeyProjection> newOrders,
            List<AmountByKeyProjection> cancellations,
            List<AmountByKeyProjection> revenue
    ) {
        Map<String, BigDecimal> newOrdersByMonth = toMap(newOrders);
        Map<String, BigDecimal> cancellationsByMonth = toMap(cancellations);
        Map<String, BigDecimal> revenueByMonth = toMap(revenue);

        return months.stream()
                .map(month -> {
                    String key = month.toString();
                    BigDecimal newOrderAmount = newOrdersByMonth.getOrDefault(key, BigDecimal.ZERO);
                    BigDecimal cancellationAmount = cancellationsByMonth.getOrDefault(key, BigDecimal.ZERO);
                    return new MonthlyTrend(
                            key,
                            newOrderAmount,
                            cancellationAmount,
                            newOrderAmount.subtract(cancellationAmount),
                            revenueByMonth.getOrDefault(key, BigDecimal.ZERO),
                            month.equals(YearMonth.from(today))
                    );
                })
                .toList();
    }

    /**
     * 增加最多、減少最多各取前三；變化為 0 的不列，同額時依代碼排序，結果才固定
     *
     * @param customers 客戶的本期與前期營收
     * @param items     料號的本期與前期營收
     * @return 增減貢獻
     */
    public Contributions toContributions(List<ContributionProjection> customers, List<ContributionProjection> items) {
        List<Contribution> customerChanges = toChanges(customers);
        List<Contribution> itemChanges = toChanges(items);

        return new Contributions(
                increases(customerChanges),
                decreases(customerChanges),
                increases(itemChanges),
                decreases(itemChanges)
        );
    }

    /**
     * @param lines          還有未出貨量的 SO 明細
     * @param supplyStatuses SO 明細 ID → 供應狀態
     * @param today          交期早於今天的算逾期未出貨
     * @return 未出貨合計、逾期的部分、缺料影響，以及依供應狀態的拆解
     */
    public Backlog toBacklog(
            List<BacklogLineProjection> lines,
            Map<Long, SupplyStatus> supplyStatuses,
            LocalDate today
    ) {
        List<BacklogLineProjection> overdue = lines.stream()
                .filter(line -> line.isOverdue(today))
                .toList();
        List<BacklogLineProjection> shortageImpact = lines.stream()
                .filter(line -> supplyStatuses.get(line.salesOrderLineId()).affectsShipment())
                .toList();

        //六個狀態都列出來，沒有明細的是 0，前端不用自己補
        List<SupplyStatusAmount> bySupplyStatus = Arrays.stream(SupplyStatus.values())
                .map(status -> {
                    List<BacklogLineProjection> matched = lines.stream()
                            .filter(line -> supplyStatuses.get(line.salesOrderLineId()) == status)
                            .toList();
                    return new SupplyStatusAmount(status, sumAmount(matched), matched.size());
                })
                .toList();

        return new Backlog(
                sumAmount(lines),
                lines.size(),
                sumAmount(overdue),
                overdue.size(),
                sumAmount(shortageImpact),
                shortageImpact.size(),
                (int) shortageImpact.stream().map(BacklogLineProjection::companyItemId).distinct().count(),
                bySupplyStatus
        );
    }

    private static OnTimeStats toOnTimeStats(OnTimeDeliveryProjection onTime) {
        if (onTime.shippedLineCount() == 0) {
            return new OnTimeStats(0, 0, null, AnalyticsNote.INSUFFICIENT_DATA);
        }

        return new OnTimeStats(
                onTime.shippedLineCount(),
                onTime.onTimeLineCount(),
                AnalyticsRules.ratio(onTime.onTimeLineCount(), onTime.shippedLineCount()),
                null
        );
    }

    private static List<Contribution> toChanges(List<ContributionProjection> rows) {
        return rows.stream()
                .map(row -> new Contribution(
                        row.id(),
                        row.code(),
                        row.name(),
                        row.currentAmount(),
                        row.previousAmount(),
                        row.currentAmount().subtract(row.previousAmount())
                ))
                .toList();
    }

    private static List<Contribution> increases(List<Contribution> contributions) {
        return contributions.stream()
                .filter(contribution -> contribution.change().signum() > 0)
                .sorted(Comparator.comparing(Contribution::change).reversed().thenComparing(Contribution::code))
                .limit(TOP_CONTRIBUTIONS)
                .toList();
    }

    //變化是負數，越小代表減少越多
    private static List<Contribution> decreases(List<Contribution> contributions) {
        return contributions.stream()
                .filter(contribution -> contribution.change().signum() < 0)
                .sorted(Comparator.comparing(Contribution::change).thenComparing(Contribution::code))
                .limit(TOP_CONTRIBUTIONS)
                .toList();
    }

    private static Map<String, BigDecimal> toMap(List<AmountByKeyProjection> rows) {
        return rows.stream().collect(Collectors.toMap(AmountByKeyProjection::key, AmountByKeyProjection::amount));
    }

    private static BigDecimal sumAmount(List<BacklogLineProjection> lines) {
        return lines.stream().map(BacklogLineProjection::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 一個期間的四組合計；service 本期與前期各查一次，交給 toComparison 比較
     */
    public record PeriodTotals(
            RevenueTotalsProjection revenue,
            NewOrderTotalsProjection newOrders,
            BigDecimal cancellationAmount,
            OnTimeDeliveryProjection onTime
    ) {

        BigDecimal netOrderAmount() {
            return newOrders.amount().subtract(cancellationAmount);
        }

        //沒有 SO 時算不出平均，回 null
        BigDecimal averageOrderAmount() {
            if (newOrders.orderCount() == 0) {
                return null;
            }
            return newOrders.amount().divide(BigDecimal.valueOf(newOrders.orderCount()), 2, RoundingMode.HALF_UP);
        }
    }
}
