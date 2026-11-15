package com.smartdistribution.approval.dto.response;

import com.smartdistribution.approval.entity.ApprovalActionType;
import com.smartdistribution.approval.entity.ApprovalTargetType;

import java.time.Instant;
import java.util.List;

/**
 * 一筆資料所有輪次的送審、核准、退回紀錄，依時間排序
 * 給看得到這筆資料的人使用，不含只給核准者看的提示
 */
public record ApprovalHistoryResponse(
        ApprovalTargetType targetType,
        Long targetId,
        List<Entry> entries
) {

    public record Entry(
            Long approvalRequestId,
            int roundNo,
            ApprovalActionType actionType,
            ApprovalUserResponse actor,
            String comment,
            Instant createdAt
    ) {
    }
}
