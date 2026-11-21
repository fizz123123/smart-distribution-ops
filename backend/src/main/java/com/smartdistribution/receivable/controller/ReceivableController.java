package com.smartdistribution.receivable.controller;

import com.smartdistribution.receivable.dto.request.ConfirmReceivableRequest;
import com.smartdistribution.receivable.dto.request.CorrectExternalInvoiceRequest;
import com.smartdistribution.receivable.dto.response.OutstandingReceivableResponse;
import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.receivable.service.DueStatus;
import com.smartdistribution.receivable.service.ReceivableService;
import com.smartdistribution.shared.web.response.PageResponse;
import com.smartdistribution.shared.web.sort.SortWhitelist;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReceivableController {

    //應收清單：預設依到期日，先到期的在前；客戶與未收餘額不在應收表頭上，請用篩選
    private static final SortWhitelist RECEIVABLE_SORTS = SortWhitelist.builder()
            .field("dueDate", "dueDate")
            .field("receivableNumber", "receivableNumber")
            .field("externalInvoiceDate", "externalInvoiceDate")
            .field("status", "status")
            .defaultSort(Sort.Direction.ASC, "dueDate")
            .build();

    private final ReceivableService receivableService;

    @GetMapping("/receivables")
    public ResponseEntity<PageResponse<ReceivableSummaryResponse>> findReceivables(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReceivableStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) DueStatus dueStatus,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(receivableService.findReceivables(
                keyword, status, customerId, dueStatus, RECEIVABLE_SORTS.apply(pageable)
        ));
    }

    @GetMapping("/receivables/{receivableId}")
    public ResponseEntity<ReceivableDetailResponse> findReceivable(@PathVariable Long receivableId) {
        return ResponseEntity.ok(receivableService.findReceivable(receivableId));
    }

    //登錄收款時用：掛在客戶底下，表達「這個客戶還欠哪些」
    @GetMapping("/customers/{customerId}/open-receivables")
    public ResponseEntity<List<OutstandingReceivableResponse>> findOutstandingReceivables(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(receivableService.findOutstandingReceivables(customerId));
    }

    @PostMapping("/receivables/{receivableId}/confirm")
    public ResponseEntity<ReceivableDetailResponse> confirmReceivable(
            @PathVariable Long receivableId,
            @Valid @RequestBody ConfirmReceivableRequest request
    ) {
        return ResponseEntity.ok(receivableService.confirmReceivable(receivableId, request));
    }

    @PutMapping("/receivables/{receivableId}/external-invoice")
    public ResponseEntity<ReceivableDetailResponse> correctExternalInvoice(
            @PathVariable Long receivableId,
            @Valid @RequestBody CorrectExternalInvoiceRequest request
    ) {
        return ResponseEntity.ok(receivableService.correctExternalInvoice(receivableId, request));
    }
}
