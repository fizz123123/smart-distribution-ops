package com.smartdistribution.approval.mapper;

import com.smartdistribution.approval.dto.response.ApprovalActionResponse;
import com.smartdistribution.approval.dto.response.ApprovalSummaryResponse;
import com.smartdistribution.approval.dto.response.ApprovalUserResponse;
import com.smartdistribution.approval.entity.ApprovalAction;
import com.smartdistribution.approval.entity.ApprovalRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApprovalMapper {

    public ApprovalSummaryResponse toSummary(
            ApprovalRequest request,
            Map<Long, String> userNames
    ) {
        return new ApprovalSummaryResponse(
                request.getId(),
                request.getTargetType(),
                request.getTargetId(),
                request.getRoundNo(),
                request.getTargetLabel(),
                toUser(request.getTargetCreatedBy(), userNames),
                toUser(request.getSubmittedBy(), userNames),
                request.getSubmittedAt(),
                request.getStatus()
        );
    }

    public ApprovalActionResponse toAction(
            ApprovalAction action,
            Map<Long, String> userNames
    ) {
        return new ApprovalActionResponse(
                action.getId(),
                action.getActionType(),
                toUser(action.getActorId(), userNames),
                action.getComment(),
                action.getCreatedAt()
        );
    }

    private ApprovalUserResponse toUser(
            Long userId,
            Map<Long, String> userNames
    ) {
        return new ApprovalUserResponse(userId, userNames.get(userId));
    }
}
