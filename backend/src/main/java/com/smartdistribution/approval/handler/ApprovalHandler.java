package com.smartdistribution.approval.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;

public interface ApprovalHandler {

    ApprovalTargetType targetType();

    ApprovalTarget loadTarget(Long targetId);

    void onApproved(Long targetId, Long approverId);

    void onReturned(Long targetId, Long approverId);
}
