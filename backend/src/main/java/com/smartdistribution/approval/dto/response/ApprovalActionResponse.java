package com.smartdistribution.approval.dto.response;

import com.smartdistribution.approval.entity.ApprovalActionType;

import java.time.Instant;

public record ApprovalActionResponse(
        Long id,
        ApprovalActionType actionType,
        ApprovalUserResponse actor,
        String comment,
        Instant createdAt
) {
}
