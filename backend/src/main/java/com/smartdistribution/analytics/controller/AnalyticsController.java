package com.smartdistribution.analytics.controller;

import com.smartdistribution.analytics.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    //應收與逾期：以今天為準的狀態，不受期間影響
    @GetMapping("/receivables")
    public ResponseEntity<ReceivableAnalysisResponse> findReceivableAnalysis(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long responsibleSalesUserId
    ) {
        return ResponseEntity.ok(analyticsService.findReceivableAnalysis(customerId, responsibleSalesUserId));
    }
}
