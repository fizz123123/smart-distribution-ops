package com.smartdistribution.analytics.cooling.service.impl;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.common.service.AnalyticsPeriod;
import com.smartdistribution.analytics.common.service.AnalyticsScope;
import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse;
import com.smartdistribution.analytics.cooling.mapper.CoolingAnalysisMapper;
import com.smartdistribution.analytics.cooling.mapper.CoolingAnalysisMapper.EvidenceData;
import com.smartdistribution.analytics.cooling.repository.CoolingAnalyticsRepository;
import com.smartdistribution.analytics.cooling.service.CoolingAnalyticsService;
import com.smartdistribution.analytics.cooling.service.CoolingRules;
import com.smartdistribution.sales.service.SalesOrderSupplyService;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoolingAnalyticsServiceImpl implements CoolingAnalyticsService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final CoolingAnalyticsRepository coolingAnalyticsRepository;
    private final CoolingAnalysisMapper coolingAnalysisMapper;
    private final AnalyticsScope analyticsScope;
    private final SalesOrderSupplyService salesOrderSupplyService;

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SALES_VIEW_ALL', 'ANALYTICS_SALES_VIEW')")
    public CoolingAnalysisResponse findCoolingAnalysis(
            LocalDate asOf,
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long responsibleSalesUserId
    ) {
        LocalDate today = LocalDate.now(TAIPEI);
        LocalDate baseDate = AnalyticsPeriod.resolveAsOf(asOf, today);
        AnalyticsPeriod period = AnalyticsPeriod.resolve(from, to, today);
        //交易降溫看的是客戶，不用料號篩選
        AnalyticsFilter filter = new AnalyticsFilter(
                customerId,
                null,
                analyticsScope.resolveResponsibleSales(responsibleSalesUserId)
        );

        return coolingAnalysisMapper.toResponse(
                baseDate,
                period,
                coolingAnalyticsRepository.findCustomerTrades(filter, baseDate),
                coolingAnalyticsRepository.findRevenueByCustomer(
                        filter, AnalyticsPeriod.startOfLastMonths(baseDate, CoolingRules.REVENUE_MONTHS), baseDate
                ),
                new EvidenceData(
                        coolingAnalyticsRepository.findRevenueByCustomer(filter, period.from(), period.to()),
                        coolingAnalyticsRepository.findRevenueByCustomer(
                                filter, period.previousFrom(), period.previousTo()
                        ),
                        coolingAnalyticsRepository.findNewOrdersByCustomer(filter, period.from(), period.to()),
                        coolingAnalyticsRepository.findNewOrdersByCustomer(
                                filter, period.previousFrom(), period.previousTo()
                        ),
                        coolingAnalyticsRepository.findCancellationCounts(filter, period.from(), period.to()),
                        coolingAnalyticsRepository.findLateShipmentCounts(filter, period.from(), period.to()),
                        countShortageLines(filter)
                )
        );
    }

    /**
     * 缺料用銷售模組的尚待規劃算，和 SO 詳情一致；只算篩選範圍內還沒出貨的明細
     *
     * @return 客戶 ID → 目前還有尚待規劃的 SO 明細數
     */
    private Map<Long, Integer> countShortageLines(AnalyticsFilter filter) {
        List<Long> backlogLineIds = coolingAnalyticsRepository.findBacklogLineIds(filter);

        return salesOrderSupplyService.findLineSupplies(backlogLineIds).stream()
                .filter(line -> line.unplannedQty().signum() > 0)
                .collect(Collectors.groupingBy(ApprovedLineSupply::customerId, Collectors.summingInt(line -> 1)));
    }
}
