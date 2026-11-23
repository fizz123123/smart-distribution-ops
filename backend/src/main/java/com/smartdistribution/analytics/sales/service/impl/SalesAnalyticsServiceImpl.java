package com.smartdistribution.analytics.sales.service.impl;

import com.smartdistribution.analytics.common.dto.response.AnalyticsPeriodResponse;
import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.common.service.AnalyticsPeriod;
import com.smartdistribution.analytics.common.service.AnalyticsScope;
import com.smartdistribution.analytics.common.service.SupplyStatus;
import com.smartdistribution.analytics.sales.dto.response.BacklogSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.CancellationSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.NewOrderSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.RevenueSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.Backlog;
import com.smartdistribution.analytics.sales.mapper.SalesAnalysisMapper;
import com.smartdistribution.analytics.sales.mapper.SalesAnalysisMapper.PeriodTotals;
import com.smartdistribution.analytics.sales.mapper.SalesSourceMapper;
import com.smartdistribution.analytics.sales.repository.SalesAnalyticsRepository;
import com.smartdistribution.analytics.sales.repository.SalesSourceRepository;
import com.smartdistribution.analytics.sales.repository.projection.BacklogLineProjection;
import com.smartdistribution.analytics.sales.service.SalesAnalyticsService;
import com.smartdistribution.sales.service.SalesOrderSupplyService;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final SalesOrderSupplyService salesOrderSupplyService;

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
                toBacklog(filter, today)
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
        rejectSort(pageable);
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
        rejectSort(pageable);
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
        rejectSort(pageable);
        AnalyticsPeriod period = AnalyticsPeriod.resolve(from, to, LocalDate.now(TAIPEI));
        AnalyticsFilter filter = toFilter(customerId, companyItemId, responsibleSalesUserId);

        return PageResponse.from(salesSourceRepository
                .findCancellationSources(filter, period.from(), period.to(), pageable)
                .map(salesSourceMapper::toResponse));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SALES_VIEW_ALL', 'ANALYTICS_SALES_VIEW')")
    public PageResponse<BacklogSourceResponse> findBacklogSources(
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId,
            SupplyStatus supplyStatus,
            boolean overdueOnly,
            Pageable pageable
    ) {
        rejectSort(pageable);
        LocalDate today = LocalDate.now(TAIPEI);
        AnalyticsFilter filter = toFilter(customerId, companyItemId, responsibleSalesUserId);
        List<BacklogLineProjection> backlogLines = salesAnalyticsRepository.findBacklogLines(filter);
        Map<Long, SupplyStatus> supplyStatuses = findSupplyStatuses(backlogLines, today);

        List<BacklogSourceResponse> sources = backlogLines.stream()
                .filter(line -> supplyStatus == null || supplyStatuses.get(line.salesOrderLineId()) == supplyStatus)
                .filter(line -> !overdueOnly || line.isOverdue(today))
                .map(line -> salesSourceMapper.toResponse(line, supplyStatuses.get(line.salesOrderLineId()), today))
                .toList();

        return PageResponse.from(toPage(sources, pageable));
    }

    //來源明細的排序固定；帶了 sort 就擋下，和其他清單 API 遇到不支援的排序一樣回 400
    private static void rejectSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "來源明細的排序固定，不接受 sort。");
        }
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

    //已接單未出貨：先查未出貨的明細，再只算這幾筆的供應狀態
    private Backlog toBacklog(AnalyticsFilter filter, LocalDate today) {
        List<BacklogLineProjection> backlogLines = salesAnalyticsRepository.findBacklogLines(filter);
        return salesAnalysisMapper.toBacklog(backlogLines, findSupplyStatuses(backlogLines, today), today);
    }

    /**
     * 供應狀態要用銷售模組的尚待規劃算，不在 SQL 重算一次；只算未出貨的這幾筆明細
     * 和未出貨明細在同一個交易裡讀，兩邊看到的已核准明細相同
     *
     * @return SO 明細 ID → 供應狀態
     */
    private Map<Long, SupplyStatus> findSupplyStatuses(List<BacklogLineProjection> backlogLines, LocalDate today) {
        List<Long> lineIds = backlogLines.stream().map(BacklogLineProjection::salesOrderLineId).toList();
        return salesOrderSupplyService.findLineSupplies(lineIds).stream()
                .collect(Collectors.toMap(
                        ApprovedLineSupply::salesOrderLineId,
                        supply -> SupplyStatus.of(supply, today)
                ));
    }

    private static <T> Page<T> toPage(List<T> all, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }
}
