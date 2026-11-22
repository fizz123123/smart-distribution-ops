package com.smartdistribution.analytics.overview.controller;

import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse;
import com.smartdistribution.analytics.overview.service.OverviewAnalyticsService;
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
public class OverviewAnalyticsController {

    private final OverviewAnalyticsService overviewAnalyticsService;

    //營運總覽：只有營運主管能看；其他角色由前端依權限直接顯示看得到的詳情
    @GetMapping("/overview")
    public ResponseEntity<OverviewAnalysisResponse> findOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) Long responsibleSalesUserId
    ) {
        return ResponseEntity.ok(overviewAnalyticsService.findOverview(
                from, to, customerId, companyItemId, responsibleSalesUserId
        ));
    }
}
