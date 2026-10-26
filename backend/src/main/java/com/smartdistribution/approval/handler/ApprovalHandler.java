package com.smartdistribution.approval.handler;

import com.smartdistribution.approval.entity.ApprovalTargetType;

public interface ApprovalHandler {

    ApprovalTargetType targetType();

    ApprovalTarget loadTarget(Long targetId);

    /**
     * 核准後執行目標的效果。approvalRequestId 是這次核准的申請，需要記錄來源的模組使用
     * （例如 SO 核准產生的預留事件指向核准申請）；其他模組可以不用。
     */
    void onApproved(Long targetId, Long approverId, Long approvalRequestId);

    void onReturned(Long targetId, Long approverId);
}
