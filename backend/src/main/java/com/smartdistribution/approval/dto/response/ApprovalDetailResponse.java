package com.smartdistribution.approval.dto.response;

import com.smartdistribution.approval.entity.ApprovalActionType;

import java.time.Instant;
import java.util.List;

public record ApprovalDetailResponse(
        ApprovalSummaryResponse request,
        List<String> warnings,
        List<Action> actions
) {

    public record Action(
            Long id,
            ApprovalActionType actionType,
            ApprovalUserResponse actor,
            String comment,
            Instant createdAt
    ) {
    }
}
