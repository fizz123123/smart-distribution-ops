package com.smartdistribution.analytics.mapper;

import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.AgingGroup;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.DueGroup;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.DueReceivable;
import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse.Summary;
import com.smartdistribution.analytics.repository.projection.OpenReceivableProjection;
import com.smartdistribution.analytics.repository.projection.ReceivableTotalsProjection;
import com.smartdistribution.analytics.service.AgingBucket;
import com.smartdistribution.receivable.service.DueStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 把應收的查詢結果組成分析回應：到期分組、帳齡、即將到期與逾期明細
 * 到期狀態沿用應收模組的 DueStatus，儀表板和應收清單的判斷只有一份
 */
@Component
public class ReceivableAnalysisMapper {

    /**
     * @param today           今天（台北時間），到期與帳齡都以它為準
     * @param totals          正式應收的合計與待確認的草稿
     * @param openReceivables 還沒收清的應收，依到期日排序
     * @return 應收分析
     */
    public ReceivableAnalysisResponse toResponse(
            LocalDate today,
            ReceivableTotalsProjection totals,
            List<OpenReceivableProjection> openReceivables
    ) {
        return new ReceivableAnalysisResponse(
                today,
                new Summary(
                        totals.totalAmount(),
                        totals.outstandingAmount(),
                        totals.pendingCount(),
                        totals.pendingAmount(),
                        toDueGroup(withDueStatus(openReceivables, DueStatus.DUE_SOON, today)),
                        toDueGroup(withDueStatus(openReceivables, DueStatus.OVERDUE, today))
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
