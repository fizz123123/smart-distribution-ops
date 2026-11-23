package com.smartdistribution.analytics.shortage.service.impl;

import com.smartdistribution.analytics.common.service.AnalyticsPeriod;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse;
import com.smartdistribution.analytics.shortage.mapper.ShortageAnalysisMapper;
import com.smartdistribution.analytics.shortage.repository.ShortageAnalyticsRepository;
import com.smartdistribution.analytics.shortage.service.ShortageAnalyticsService;
import com.smartdistribution.analytics.shortage.service.ShortageRules;
import com.smartdistribution.sales.service.SalesOrderSupplyService;
import com.smartdistribution.sales.service.SalesOrderSupplyService.ApprovedLineSupply;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortageAnalyticsServiceImpl implements ShortageAnalyticsService {

    //平均採購週期與供應商表現看最近幾個月
    private static final int HISTORY_MONTHS = 6;

    private final ShortageAnalyticsRepository shortageAnalyticsRepository;
    private final ShortageAnalysisMapper shortageAnalysisMapper;
    private final SalesOrderSupplyService salesOrderSupplyService;
    private final Clock clock;

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_SHORTAGE_VIEW')")
    public ShortageAnalysisResponse findShortageAnalysis(Long companyItemId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate historyFrom = AnalyticsPeriod.startOfLastMonths(today, HISTORY_MONTHS);
        //尚待規劃用銷售模組的算法，和 SO 詳情、缺料清單一致
        List<ApprovedLineSupply> supplies = salesOrderSupplyService.findApprovedLineSupplies(companyItemId);

        return shortageAnalysisMapper.toResponse(
                today,
                shortageAnalyticsRepository.findItemStocks(
                        companyItemId,
                        today.minusDays(ShortageRules.USAGE_DAYS - 1),
                        today,
                        historyFrom
                ),
                supplies,
                shortageAnalyticsRepository.findOpenPurchaseLines(companyItemId),
                shortageAnalyticsRepository.findCustomerNames(
                        supplies.stream().map(ApprovedLineSupply::customerId).collect(Collectors.toSet())
                ),
                shortageAnalyticsRepository.findSupplierPerformance(companyItemId, today, historyFrom)
        );
    }
}
