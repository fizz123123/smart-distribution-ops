package com.smartdistribution.analytics.receivable.service.impl;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.receivable.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.receivable.mapper.ReceivableAnalysisMapper;
import com.smartdistribution.analytics.receivable.repository.ReceivableAnalyticsRepository;
import com.smartdistribution.analytics.receivable.service.ReceivableAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivableAnalyticsServiceImpl implements ReceivableAnalyticsService {

    private final ReceivableAnalyticsRepository receivableAnalyticsRepository;
    private final ReceivableAnalysisMapper receivableAnalysisMapper;
    private final Clock clock;

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_RECEIVABLE_VIEW')")
    public ReceivableAnalysisResponse findReceivableAnalysis(Long customerId, Long responsibleSalesUserId) {
        //應收沒有料號的篩選；財務看全部業務，所以不經過 AnalyticsScope
        AnalyticsFilter filter = new AnalyticsFilter(customerId, null, responsibleSalesUserId);

        return receivableAnalysisMapper.toResponse(
                LocalDate.now(clock),
                receivableAnalyticsRepository.findTotals(filter),
                receivableAnalyticsRepository.findOpenReceivables(filter)
        );
    }
}
