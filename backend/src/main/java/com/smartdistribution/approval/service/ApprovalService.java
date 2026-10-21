package com.smartdistribution.approval.service;

import com.smartdistribution.approval.dto.request.ApproveRequest;
import com.smartdistribution.approval.dto.request.ReturnRequest;
import com.smartdistribution.approval.dto.response.ApprovalDetailResponse;
import com.smartdistribution.approval.dto.response.ApprovalSummaryResponse;
import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ApprovalService {

    void submit(
            ApprovalTargetType targetType,
            Long targetId,
            Long submitterId
    );

    PageResponse<ApprovalSummaryResponse> findMyPending(Pageable pageable);

    ApprovalDetailResponse findDetail(Long approvalRequestId);

    ApprovalDetailResponse approve(
            Long approvalRequestId,
            ApproveRequest request
    );

    ApprovalDetailResponse returnForRevision(
            Long approvalRequestId,
            ReturnRequest request
    );
}
