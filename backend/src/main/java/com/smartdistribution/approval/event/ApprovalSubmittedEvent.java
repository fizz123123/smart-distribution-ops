package com.smartdistribution.approval.event;

import com.smartdistribution.approval.entity.ApprovalTargetType;

import java.time.Instant;

/**
 * 已建立一輪核准申請；交易提交後通知可以核准的主管
 */
public record ApprovalSubmittedEvent(
        Long approvalRequestId,
        ApprovalTargetType targetType,
        Long targetId,
        int roundNo,
        String targetLabel,
        Long targetCreatedBy,
        Long submittedBy,
        Instant submittedAt
) {
}
