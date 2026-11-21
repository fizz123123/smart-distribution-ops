package com.smartdistribution.analytics.service.impl;

import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.mapper.ReceivableAnalysisMapper;
import com.smartdistribution.analytics.repository.ReceivableAnalyticsRepository;
import com.smartdistribution.analytics.service.ReceivableAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivableAnalyticsServiceImpl implements ReceivableAnalyticsService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final ReceivableAnalyticsRepository receivableAnalyticsRepository;
    private final ReceivableAnalysisMapper receivableAnalysisMapper;

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_RECEIVABLE_VIEW')")
    public ReceivableAnalysisResponse findReceivableAnalysis(Long customerId, Long responsibleSalesUserId) {
        return receivableAnalysisMapper.toResponse(
                LocalDate.now(TAIPEI),
                receivableAnalyticsRepository.findTotals(customerId, responsibleSalesUserId),
                receivableAnalyticsRepository.findOpenReceivables(customerId, responsibleSalesUserId)
        );
    }
}
