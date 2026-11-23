package com.smartdistribution.analytics.cooling.controller;

import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse;
import com.smartdistribution.analytics.cooling.service.CoolingAnalyticsService;
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
public class CoolingAnalyticsController {

    private final CoolingAnalyticsService coolingAnalyticsService;

    //客戶交易降溫：週期與等級依 asOf 計算，from、to 只影響補充證據
    @GetMapping("/customer-cooling")
    public ResponseEntity<CoolingAnalysisResponse> findCoolingAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long responsibleSalesUserId
    ) {
        return ResponseEntity.ok(coolingAnalyticsService.findCoolingAnalysis(
                asOf, from, to, customerId, responsibleSalesUserId
        ));
    }
}
