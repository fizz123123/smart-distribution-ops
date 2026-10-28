package com.smartdistribution.audit.service;

import com.smartdistribution.audit.entity.AuditActionType;

public interface AuditService {

    //排程等系統自動執行的操作沒有操作者，稽核紀錄的操作者 ID 記為 0
    Long SYSTEM_ACTOR_ID = 0L;

    void record(
            Long actorId,
            AuditActionType actionType,
            Long targetId,
            String summary,
            String beforeData,
            String afterData,
            String reason
    );
}
