package com.smartdistribution.approval.event;

import com.smartdistribution.approval.entity.ApprovalStatus;
import com.smartdistribution.approval.entity.ApprovalTargetType;

import java.time.Instant;

/**
 * 核准申請已核准或退回；交易提交後通知建立者與送審者
 *
 * @param result  APPROVED 或 RETURNED
 * @param comment 核准意見或退回原因，可能是 null
 */
public record ApprovalDecidedEvent(
        Long approvalRequestId,
        ApprovalTargetType targetType,
        Long targetId,
        String targetLabel,
        Long targetCreatedBy,
        Long submittedBy,
        ApprovalStatus result,
        Long actorId,
        String comment,
        Instant decidedAt
) {
}
