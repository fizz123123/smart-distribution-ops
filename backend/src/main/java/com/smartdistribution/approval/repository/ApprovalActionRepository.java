package com.smartdistribution.approval.repository;

import com.smartdistribution.approval.entity.ApprovalAction;
import com.smartdistribution.approval.entity.ApprovalTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {

    @Query("""
            SELECT action
            FROM ApprovalAction action
            JOIN FETCH action.approvalRequest request
            WHERE request.targetType = :targetType
              AND request.targetId = :targetId
            ORDER BY action.createdAt ASC, action.id ASC
            """)
    List<ApprovalAction> findHistory(
            @Param("targetType") ApprovalTargetType targetType,
            @Param("targetId") Long targetId
    );
}
