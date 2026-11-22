package com.smartdistribution.analytics.cooling.mapper;

import com.smartdistribution.analytics.common.dto.response.AnalyticsPeriodResponse;
import com.smartdistribution.analytics.common.service.AnalyticsNote;
import com.smartdistribution.analytics.common.service.AnalyticsPeriod;
import com.smartdistribution.analytics.common.service.MetricChanges;
import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse;
import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse.CustomerCooling;
import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse.Evidence;
import com.smartdistribution.analytics.cooling.repository.projection.CustomerOrdersProjection;
import com.smartdistribution.analytics.cooling.repository.projection.CustomerTradeProjection;
import com.smartdistribution.analytics.cooling.service.CoolingLevel;
import com.smartdistribution.analytics.cooling.service.CoolingRules;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 把交易紀錄算成週期、比值與等級，再附上補充證據，依等級與平均月營收排序
 */
@Component
public class CoolingAnalysisMapper {

    /**
     * @param asOf             分析基準日
     * @param period           補充證據的本期與前期
     * @param trades           每個客戶到基準日為止的交易紀錄
     * @param sixMonthRevenue  客戶 ID → 基準日往前六個月的營收
     * @param evidence         補充證據的查詢結果
     * @return 交易降溫
     */
    public CoolingAnalysisResponse toResponse(
            LocalDate asOf,
            AnalyticsPeriod period,
            List<CustomerTradeProjection> trades,
            Map<Long, BigDecimal> sixMonthRevenue,
            EvidenceData evidence
    ) {
        List<CustomerCooling> customers = trades.stream()
                .map(trade -> toCustomerCooling(trade, asOf, sixMonthRevenue, evidence))
                .sorted(Comparator
                        .comparingInt(CoolingAnalysisMapper::rank)
                        .thenComparing(CustomerCooling::averageMonthlyRevenue, Comparator.reverseOrder())
                        .thenComparing(CustomerCooling::customerCode))
                .toList();

        return new CoolingAnalysisResponse(asOf, AnalyticsPeriodResponse.from(period), customers);
    }

    private static CustomerCooling toCustomerCooling(
            CustomerTradeProjection trade,
            LocalDate asOf,
            Map<Long, BigDecimal> sixMonthRevenue,
            EvidenceData evidence
    ) {
        Long customerId = trade.customerId();
        long daysSinceLastTrade = ChronoUnit.DAYS.between(trade.lastTradeDate(), asOf);
        BigDecimal cycleRatio = CoolingRules.cycleRatio(
                daysSinceLastTrade, trade.firstTradeDate(), trade.lastTradeDate(), trade.tradeDayCount()
        );
        CustomerOrdersProjection currentOrders = evidence.currentOrders().get(customerId);
        CustomerOrdersProjection previousOrders = evidence.previousOrders().get(customerId);

        return new CustomerCooling(
                customerId,
                trade.customerCode(),
                trade.customerName(),
                trade.active(),
                //停用後本來就不會再下單，評等級只會變成假警報
                trade.active() ? CoolingLevel.of(cycleRatio) : null,
                cycleRatio == null ? AnalyticsNote.INSUFFICIENT_DATA : null,
                trade.lastTradeDate(),
                daysSinceLastTrade,
                trade.approvedOrderCount(),
                trade.tradeDayCount(),
                CoolingRules.averageCycleDays(trade.firstTradeDate(), trade.lastTradeDate(), trade.tradeDayCount()),
                cycleRatio,
                CoolingRules.averageMonthlyRevenue(sixMonthRevenue.getOrDefault(customerId, BigDecimal.ZERO)),
                new Evidence(
                        MetricChanges.compare(orderCount(currentOrders), orderCount(previousOrders)),
                        MetricChanges.compare(orderAmount(currentOrders), orderAmount(previousOrders)),
                        MetricChanges.compare(
                                evidence.currentRevenue().getOrDefault(customerId, BigDecimal.ZERO),
                                evidence.previousRevenue().getOrDefault(customerId, BigDecimal.ZERO)
                        ),
                        evidence.shortageLineCounts().getOrDefault(customerId, 0),
                        evidence.cancellationCounts().getOrDefault(customerId, 0),
                        evidence.lateShipmentCounts().getOrDefault(customerId, 0)
                )
        );
    }

    //HIGH、MEDIUM、未達警示（含停用）、資料不足
    private static int rank(CustomerCooling customer) {
        if (customer.level() == CoolingLevel.HIGH) {
            return 0;
        }
        if (customer.level() == CoolingLevel.MEDIUM) {
            return 1;
        }
        return customer.note() == null ? 2 : 3;
    }

    private static long orderCount(CustomerOrdersProjection orders) {
        return orders == null ? 0 : orders.orderCount();
    }

    private static BigDecimal orderAmount(CustomerOrdersProjection orders) {
        return orders == null ? BigDecimal.ZERO : orders.amount();
    }

    /**
     * 補充證據的查詢結果，都是客戶 ID → 數值；service 查好交給 toResponse
     *
     * @param shortageLineCounts 目前還有尚待規劃的 SO 明細數
     */
    public record EvidenceData(
            Map<Long, BigDecimal> currentRevenue,
            Map<Long, BigDecimal> previousRevenue,
            Map<Long, CustomerOrdersProjection> currentOrders,
            Map<Long, CustomerOrdersProjection> previousOrders,
            Map<Long, Integer> cancellationCounts,
            Map<Long, Integer> lateShipmentCounts,
            Map<Long, Integer> shortageLineCounts
    ) {
    }
}
