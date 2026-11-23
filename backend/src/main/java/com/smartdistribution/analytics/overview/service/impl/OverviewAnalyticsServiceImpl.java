package com.smartdistribution.analytics.overview.service.impl;

import com.smartdistribution.analytics.cooling.service.CoolingAnalyticsService;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse;
import com.smartdistribution.analytics.overview.mapper.OverviewAnalysisMapper;
import com.smartdistribution.analytics.overview.service.OverviewAnalyticsService;
import com.smartdistribution.analytics.receivable.service.ReceivableAnalyticsService;
import com.smartdistribution.analytics.sales.service.SalesAnalyticsService;
import com.smartdistribution.analytics.shortage.service.ShortageAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 總覽呼叫和詳情頁同一組服務方法，所以總覽和詳情的數字一定相同；沒有自己的 repository
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewAnalyticsServiceImpl implements OverviewAnalyticsService {

    private final SalesAnalyticsService salesAnalyticsService;
    private final ShortageAnalyticsService shortageAnalyticsService;
    private final CoolingAnalyticsService coolingAnalyticsService;
    private final ReceivableAnalyticsService receivableAnalyticsService;
    private final OverviewAnalysisMapper overviewAnalysisMapper;

    @Override
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW_ALL')")
    public OverviewAnalysisResponse findOverview(
            LocalDate from,
            LocalDate to,
            Long customerId,
            Long companyItemId,
            Long responsibleSalesUserId
    ) {
        return overviewAnalysisMapper.toResponse(
                salesAnalyticsService.findSalesAnalysis(from, to, customerId, companyItemId, responsibleSalesUserId),
                shortageAnalyticsService.findShortageAnalysis(companyItemId),
                //總覽的交易降溫以今天為準；指定過去的基準日要到交易降溫詳情
                coolingAnalyticsService.findCoolingAnalysis(null, from, to, customerId, responsibleSalesUserId),
                receivableAnalyticsService.findReceivableAnalysis(customerId, responsibleSalesUserId)
        );
    }
}
