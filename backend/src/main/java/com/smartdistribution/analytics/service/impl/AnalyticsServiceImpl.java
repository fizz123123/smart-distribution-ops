package com.smartdistribution.analytics.service.impl;

import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.AgingGroup;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.DueGroup;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.DueReceivable;
import com.smartdistribution.analytics.repository.projection.OpenReceivableProjection;
import com.smartdistribution.analytics.repository.ReceivableAnalyticsRepository;
import com.smartdistribution.analytics.repository.projection.ReceivableTotalsProjection;
import com.smartdistribution.analytics.service.AgingBucket;
import com.smartdistribution.analytics.service.AnalyticsService;
import com.smartdistribution.receivable.service.DueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final ReceivableAnalyticsRepository receivableAnalyticsRepository;

    @Override
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW_ALL', 'ANALYTICS_RECEIVABLE_VIEW')")
    public ReceivableAnalysisResponse findReceivableAnalysis(Long customerId, Long responsibleSalesUserId) {
        LocalDate today = LocalDate.now(TAIPEI);
        ReceivableTotalsProjection totals =
                receivableAnalyticsRepository.findTotals(customerId, responsibleSalesUserId);
        List<OpenReceivableProjection> openReceivables =
                receivableAnalyticsRepository.findOpenReceivables(customerId, responsibleSalesUserId);

        //到期狀態沿用應收模組的 DueStatus，儀表板和應收清單的判斷只有一份
        List<OpenReceivableProjection> dueSoon = withDueStatus(openReceivables, DueStatus.DUE_SOON, today);
        List<OpenReceivableProjection> overdue = withDueStatus(openReceivables, DueStatus.OVERDUE, today);

        return new ReceivableAnalysisResponse(
                today,
                new ReceivableAnalysisResponse.Summary(
                        totals.totalAmount(),
                        totals.outstandingAmount(),
                        totals.pendingCount(),
                        totals.pendingAmount(),
                        toDueGroup(dueSoon),
                        toDueGroup(overdue)
                ),
                toAging(openReceivables, today),
                openReceivables.stream()
                        .filter(receivable -> DueStatus.of(receivable.dueDate(), today) != null)
                        .map(receivable -> toDueReceivable(receivable, today))
                        .toList()
        );
    }

    private static List<OpenReceivableProjection> withDueStatus(
            List<OpenReceivableProjection> receivables,
            DueStatus dueStatus,
            LocalDate today
    ) {
        return receivables.stream()
                .filter(receivable -> DueStatus.of(receivable.dueDate(), today) == dueStatus)
                .toList();
    }

    private static DueGroup toDueGroup(List<OpenReceivableProjection> receivables) {
        return new DueGroup(
                receivables.size(),
                sumOutstanding(receivables),
                (int) receivables.stream().filter(OpenReceivableProjection::isUnconfirmed).count()
        );
    }

    //每一組都列出，沒有應收的組金額為 0，前端畫圖時不用補空白
    private static List<AgingGroup> toAging(List<OpenReceivableProjection> receivables, LocalDate today) {
        return Arrays.stream(AgingBucket.values())
                .map(bucket -> {
                    List<OpenReceivableProjection> inBucket = receivables.stream()
                            .filter(receivable -> AgingBucket.of(receivable.dueDate(), today) == bucket)
                            .toList();
                    return new AgingGroup(bucket, inBucket.size(), sumOutstanding(inBucket));
                })
                .toList();
    }

    private static DueReceivable toDueReceivable(OpenReceivableProjection receivable, LocalDate today) {
        return new DueReceivable(
                receivable.receivableId(),
                receivable.receivableNumber(),
                receivable.status(),
                DueStatus.of(receivable.dueDate(), today),
                receivable.customerId(),
                receivable.customerCode(),
                receivable.customerName(),
                receivable.salesOrderId(),
                receivable.soNumber(),
                receivable.deliveryOrderId(),
                receivable.doNumber(),
                receivable.dueDate(),
                Math.max(0, ChronoUnit.DAYS.between(receivable.dueDate(), today)),
                receivable.outstandingAmount()
        );
    }

    private static BigDecimal sumOutstanding(List<OpenReceivableProjection> receivables) {
        return receivables.stream()
                .map(OpenReceivableProjection::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
