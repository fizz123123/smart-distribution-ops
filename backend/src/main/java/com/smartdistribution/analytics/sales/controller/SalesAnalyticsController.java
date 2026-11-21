package com.smartdistribution.analytics.sales.controller;

import com.smartdistribution.analytics.sales.dto.response.CancellationSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.NewOrderSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.RevenueSourceResponse;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse;
import com.smartdistribution.analytics.sales.service.SalesAnalyticsService;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
public class SalesAnalyticsController {

    private final SalesAnalyticsService salesAnalyticsService;

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

    //營收的來源明細：期間內出貨的應收明細；排序固定為日期由新到舊，不接受 sort
    @GetMapping("/sales/revenue-sources")
    public ResponseEntity<PageResponse<RevenueSourceResponse>> findRevenueSources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) Long responsibleSalesUserId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(salesAnalyticsService.findRevenueSources(
                from, to, customerId, companyItemId, responsibleSalesUserId, pageable
        ));
    }

    //新增接單的來源明細：期間內核准的 SO
    @GetMapping("/sales/new-order-sources")
    public ResponseEntity<PageResponse<NewOrderSourceResponse>> findNewOrderSources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) Long responsibleSalesUserId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(salesAnalyticsService.findNewOrderSources(
                from, to, customerId, companyItemId, responsibleSalesUserId, pageable
        ));
    }

    //核准取消的來源明細：期間內核准的取消申請明細
    @GetMapping("/sales/cancellation-sources")
    public ResponseEntity<PageResponse<CancellationSourceResponse>> findCancellationSources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyItemId,
            @RequestParam(required = false) Long responsibleSalesUserId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(salesAnalyticsService.findCancellationSources(
                from, to, customerId, companyItemId, responsibleSalesUserId, pageable
        ));
    }
}
