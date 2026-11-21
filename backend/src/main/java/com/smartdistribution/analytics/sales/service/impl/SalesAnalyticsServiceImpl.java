package com.smartdistribution.analytics.sales.service.impl;

import com.smartdistribution.analytics.common.dto.response.AnalyticsPeriodResponse;
import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.common.service.AnalyticsPeriod;
import com.smartdistribution.analytics.common.service.AnalyticsScope;
import com.smartdistribution.analytics.sales.dto.response.CancellationSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.NewOrderSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.RevenueSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse;
import com.smartdistribution.analytics.sales.mapper.SalesAnalysisMapper;
import com.smartdistribution.analytics.sales.mapper.SalesAnalysisMapper.PeriodTotals;
import com.smartdistribution.analytics.sales.mapper.SalesSourceMapper;
import com.smartdistribution.analytics.sales.repository.SalesAnalyticsRepository;
import com.smartdistribution.analytics.sales.repository.SalesSourceRepository;
import com.smartdistribution.analytics.sales.service.SalesAnalyticsService;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesAnalyticsServiceImpl implements SalesAnalyticsService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final SalesAnalyticsRepository salesAnalyticsRepository;
    private final SalesSourceRepository salesSourceRepository;
    private final SalesAnalysisMapper salesAnalysisMapper;
    private final SalesSourceMapper salesSourceMapper;
    private final AnalyticsScope analyticsScope;

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SALES_VIEW_ALL', 'ANALYTICS_SALES_VIEW')")
    public SalesAnalysisResponse findSalesAnalysis(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId
    ) {
        LocalDate today = LocalDate.now(TAIPEI);
        AnalyticsPeriod period = AnalyticsPeriod.resolve(from, to, today);
        AnalyticsFilter filter = toFilter(customerId, companyItemId, responsibleSalesUserId);

        //近六個月不受期間篩選影響，固定從五個月前的 1 日到今天
        List<YearMonth> months = AnalyticsPeriod.recentMonths(today);
        LocalDate trendFrom = months.getFirst().atDay(1);

        return new SalesAnalysisResponse(
                AnalyticsPeriodResponse.from(period),
                salesAnalysisMapper.toComparison(
                        findPeriodTotals(filter, period.from(), period.to()),
                        findPeriodTotals(filter, period.previousFrom(), period.previousTo())
                ),
                salesAnalysisMapper.toTrend(
                        months,
                        today,
                        salesAnalyticsRepository.findMonthlyNewOrderAmounts(filter, trendFrom, today),
                        salesAnalyticsRepository.findMonthlyCancellationAmounts(filter, trendFrom, today),
                        salesAnalyticsRepository.findMonthlyRevenue(filter, trendFrom, today)
                ),
                salesAnalysisMapper.toContributions(
                        salesAnalyticsRepository.findRevenueByCustomer(
                                filter, period.from(), period.to(), period.previousFrom(), period.previousTo()
                        ),
                        salesAnalyticsRepository.findRevenueByItem(
                                filter, period.from(), period.to(), period.previousFrom(), period.previousTo()
                        )
                ),
                salesAnalysisMapper.toBacklog(salesAnalyticsRepository.findBacklogLines(filter), today)
        );
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SALES_VIEW_ALL', 'ANALYTICS_SALES_VIEW')")
    public PageResponse<RevenueSourceResponse> findRevenueSources(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            Pageable pageable
    ) {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(from, to, LocalDate.now(TAIPEI));
        AnalyticsFilter filter = toFilter(customerId, companyItemId, responsibleSalesUserId);

        return PageResponse.from(salesSourceRepository
                .findRevenueSources(filter, period.from(), period.to(), pageable)
                .map(salesSourceMapper::toResponse));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SALES_VIEW_ALL', 'ANALYTICS_SALES_VIEW')")
    public PageResponse<NewOrderSourceResponse> findNewOrderSources(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            Pageable pageable
    ) {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(from, to, LocalDate.now(TAIPEI));
        AnalyticsFilter filter = toFilter(customerId, companyItemId, responsibleSalesUserId);

        return PageResponse.from(salesSourceRepository
                .findNewOrderSources(filter, period.from(), period.to(), pageable)
                .map(salesSourceMapper::toResponse));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SALES_VIEW_ALL', 'ANALYTICS_SALES_VIEW')")
    public PageResponse<CancellationSourceResponse> findCancellationSources(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            Pageable pageable
    ) {
        AnalyticsPeriod period = AnalyticsPeriod.resolve(from, to, LocalDate.now(TAIPEI));
        AnalyticsFilter filter = toFilter(customerId, companyItemId, responsibleSalesUserId);

        return PageResponse.from(salesSourceRepository
                .findCancellationSources(filter, period.from(), period.to(), pageable)
                .map(salesSourceMapper::toResponse));
    }

    //業務只有 ANALYTICS_SALES_VIEW 時，負責業務強制是自己
    private AnalyticsFilter toFilter(Long customerId, Long companyItemId, Long responsibleSalesUserId) {
        return new AnalyticsFilter(
                customerId,
                companyItemId,
                analyticsScope.resolveResponsibleSales(responsibleSalesUserId)
        );
    }

    private PeriodTotals findPeriodTotals(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        return new PeriodTotals(
                salesAnalyticsRepository.findRevenueTotals(filter, from, to),
                salesAnalyticsRepository.findNewOrderTotals(filter, from, to),
                salesAnalyticsRepository.findCancellationAmount(filter, from, to),
                salesAnalyticsRepository.findOnTimeDelivery(filter, from, to)
        );
    }
}
