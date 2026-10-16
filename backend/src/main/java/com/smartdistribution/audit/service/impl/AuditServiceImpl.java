package com.smartdistribution.audit.service.impl;

import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.entity.AuditLog;
import com.smartdistribution.audit.entity.AuditTargetType;
import com.smartdistribution.audit.repository.AuditLogRepository;
import com.smartdistribution.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void record(
            Long actorId,
            AuditActionType actionType,
            Long targetUserId,
            String summary,
            String beforeData,
            String afterData,
            String reason
    ) {
        auditLogRepository.save(new AuditLog(
                actorId,
                actionType,
                AuditTargetType.USER,
                targetUserId,
                summary,
                beforeData,
                afterData,
                reason
        ));
    }
}
