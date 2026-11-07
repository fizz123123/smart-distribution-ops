package com.smartdistribution.receivable.controller;

import com.smartdistribution.receivable.dto.response.ReceivableDetailResponse;
import com.smartdistribution.receivable.dto.response.ReceivableSummaryResponse;
import com.smartdistribution.receivable.entity.ReceivableStatus;
import com.smartdistribution.receivable.service.ReceivableService;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receivables")
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableService receivableService;

    @GetMapping
    public ResponseEntity<PageResponse<ReceivableSummaryResponse>> findReceivables(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReceivableStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(receivableService.findReceivables(keyword, status, customerId, pageable));
    }

    @GetMapping("/{receivableId}")
    public ResponseEntity<ReceivableDetailResponse> findReceivable(@PathVariable Long receivableId) {
        return ResponseEntity.ok(receivableService.findReceivable(receivableId));
    }
}
