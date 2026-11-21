package com.smartdistribution.analytics.controller;

import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.dto.response.SalesAnalysisResponse;
import com.smartdistribution.analytics.service.ReceivableAnalyticsService;
import com.smartdistribution.analytics.service.SalesAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SalesAnalyticsService salesAnalyticsService;
    private final ReceivableAnalyticsService receivableAnalyticsService;

    //接單與營收：from、to 都不帶時是本月至今對上月同期；日期格式 2026-11-21
    @GetMapping("/sales")
    public ResponseEntity<SalesAnalysisResponse> findSalesAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) Long responsibleSalesUserId
    ) {
        return ResponseEntity.ok(salesAnalyticsService.findSalesAnalysis(
                from, to, customerId, companyItemId, responsibleSalesUserId
        ));
    }

    //應收與逾期：以今天為準的狀態，不受期間影響
    @GetMapping("/receivables")
    public ResponseEntity<ReceivableAnalysisResponse> findReceivableAnalysis(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long responsibleSalesUserId
    ) {
        return ResponseEntity.ok(receivableAnalyticsService.findReceivableAnalysis(customerId, responsibleSalesUserId));
    }
}
