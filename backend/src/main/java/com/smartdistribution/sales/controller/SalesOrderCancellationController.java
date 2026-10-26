package com.smartdistribution.sales.controller;

import com.smartdistribution.sales.dto.request.CreateSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.SubmitSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.request.WithdrawSalesOrderCancellationRequest;
import com.smartdistribution.sales.dto.response.SalesOrderCancellationResponse;
import com.smartdistribution.sales.service.SalesOrderCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 建立與列出掛在 SO 底下；單張申請的查詢與操作用取消申請自己的 ID
 * 核准與退回走共用核准 API
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SalesOrderCancellationController {

    private final SalesOrderCancellationService salesOrderCancellationService;

    @GetMapping("/sales-orders/{salesOrderId}/cancellations")
    public ResponseEntity<List<SalesOrderCancellationResponse>> findCancellations(
            @PathVariable Long salesOrderId
    ) {
        return ResponseEntity.ok(salesOrderCancellationService.findCancellations(salesOrderId));
    }

    @PostMapping("/sales-orders/{salesOrderId}/cancellations")
    public ResponseEntity<SalesOrderCancellationResponse> createCancellation(
            @PathVariable Long salesOrderId,
            @Valid @RequestBody CreateSalesOrderCancellationRequest request
    ) {
        SalesOrderCancellationResponse response =
                salesOrderCancellationService.createCancellation(salesOrderId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/sales-order-cancellations/{cancellationId}")
    public ResponseEntity<SalesOrderCancellationResponse> findCancellation(@PathVariable Long cancellationId) {
        return ResponseEntity.ok(salesOrderCancellationService.findCancellation(cancellationId));
    }

    @PatchMapping("/sales-order-cancellations/{cancellationId}")
    public ResponseEntity<SalesOrderCancellationResponse> updateCancellation(
            @PathVariable Long cancellationId,
            @Valid @RequestBody UpdateSalesOrderCancellationRequest request
    ) {
        return ResponseEntity.ok(salesOrderCancellationService.updateCancellation(cancellationId, request));
    }

    @PostMapping("/sales-order-cancellations/{cancellationId}/submit")
    public ResponseEntity<SalesOrderCancellationResponse> submitCancellation(
            @PathVariable Long cancellationId,
            @Valid @RequestBody SubmitSalesOrderCancellationRequest request
    ) {
        return ResponseEntity.ok(salesOrderCancellationService.submitCancellation(cancellationId, request));
    }

    @PostMapping("/sales-order-cancellations/{cancellationId}/withdraw")
    public ResponseEntity<SalesOrderCancellationResponse> withdrawCancellation(
            @PathVariable Long cancellationId,
            @Valid @RequestBody WithdrawSalesOrderCancellationRequest request
    ) {
        return ResponseEntity.ok(salesOrderCancellationService.withdrawCancellation(cancellationId, request));
    }
}
