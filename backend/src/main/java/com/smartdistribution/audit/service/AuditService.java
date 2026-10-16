package com.smartdistribution.audit.service;

import com.smartdistribution.audit.entity.AuditActionType;

public interface AuditService {

    void record(
            Long actorId,
            AuditActionType actionType,
            Long targetUserId,
            String summary,
            String beforeData,
            String afterData,
            String reason
    );
}
