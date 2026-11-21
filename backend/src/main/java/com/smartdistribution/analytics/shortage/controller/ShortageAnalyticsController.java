package com.smartdistribution.analytics.shortage.controller;

import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse;
import com.smartdistribution.analytics.shortage.service.ShortageAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class ShortageAnalyticsController {

    private final ShortageAnalyticsService shortageAnalyticsService;

    //缺貨與採購：以今天為準的料號風險，不受期間影響；和 /sales-orders/shortages（給採購排 PO 的 SO 明細清單）用途不同
    @GetMapping("/shortages")
    public ResponseEntity<ShortageAnalysisResponse> findShortageAnalysis(
            @RequestParam(required = false) Long companyItemId
    ) {
        return ResponseEntity.ok(shortageAnalyticsService.findShortageAnalysis(companyItemId));
    }
}
