package com.smartdistribution.approval.dto.response;

import com.smartdistribution.approval.entity.ApprovalStatus;
import com.smartdistribution.approval.entity.ApprovalTargetType;

import java.time.Instant;

public record ApprovalSummaryResponse(
        Long id,
        ApprovalTargetType targetType,
        Long targetId,
        int roundNo,
        String targetLabel,
        ApprovalUserResponse targetCreator,
        ApprovalUserResponse submitter,
        Instant submittedAt,
        ApprovalStatus status
) {
}
