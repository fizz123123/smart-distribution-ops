package com.smartdistribution.receivable.controller;

import com.smartdistribution.receivable.dto.request.ConfirmPaymentRequest;
import com.smartdistribution.receivable.dto.response.PaymentDetailResponse;
import com.smartdistribution.receivable.dto.response.PaymentSummaryResponse;
import com.smartdistribution.receivable.entity.PaymentStatus;
import com.smartdistribution.receivable.service.PaymentService.ConfirmedPayment;
import com.smartdistribution.receivable.service.PaymentService;
import com.smartdistribution.shared.web.request.ReasonRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<PageResponse<PaymentSummaryResponse>> findPayments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.findPayments(keyword, status, customerId, pageable));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> findPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.findPayment(paymentId));
    }

    //新建回 201；同一個 clientRequestId 重送時回 200 與原本那筆
    @PostMapping
    public ResponseEntity<PaymentDetailResponse> confirmPayment(@Valid @RequestBody ConfirmPaymentRequest request) {
        ConfirmedPayment result = paymentService.confirmPayment(request);

        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/{paymentId}/void")
    public ResponseEntity<PaymentDetailResponse> voidPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody ReasonRequest request
    ) {
        return ResponseEntity.ok(paymentService.voidPayment(paymentId, request));
    }
}
