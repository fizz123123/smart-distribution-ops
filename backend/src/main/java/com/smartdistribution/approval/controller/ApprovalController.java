package com.smartdistribution.approval.controller;

import com.smartdistribution.approval.dto.request.ApproveRequest;
import com.smartdistribution.approval.dto.request.ReturnRequest;
import com.smartdistribution.approval.dto.response.ApprovalDetailResponse;
import com.smartdistribution.approval.dto.response.ApprovalSummaryResponse;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approval-requests")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/my-pending")
    public ResponseEntity<PageResponse<ApprovalSummaryResponse>> findMyPending(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(approvalService.findMyPending(pageable));
    }

    @GetMapping("/{approvalRequestId}")
    public ResponseEntity<ApprovalDetailResponse> findDetail(
            @PathVariable Long approvalRequestId
    ) {
        return ResponseEntity.ok(
                approvalService.findDetail(approvalRequestId)
        );
    }

    @PostMapping("/{approvalRequestId}/approve")
    public ResponseEntity<ApprovalDetailResponse> approve(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ApproveRequest request
    ) {
        return ResponseEntity.ok(
                approvalService.approve(approvalRequestId, request)
        );
    }

    @PostMapping("/{approvalRequestId}/return")
    public ResponseEntity<ApprovalDetailResponse> returnForRevision(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ReturnRequest request
    ) {
        return ResponseEntity.ok(
                approvalService.returnForRevision(approvalRequestId, request)
        );
    }
}
