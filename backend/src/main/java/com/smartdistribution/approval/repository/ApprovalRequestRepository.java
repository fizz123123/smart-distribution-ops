package com.smartdistribution.approval.repository;

import com.smartdistribution.approval.entity.ApprovalRequest;
import com.smartdistribution.approval.entity.ApprovalStatus;
import com.smartdistribution.approval.entity.ApprovalTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    boolean existsByTargetTypeAndTargetIdAndStatus(
            ApprovalTargetType targetType,
            Long targetId,
            ApprovalStatus status
    );

    long countByTargetTypeAndTargetId(
            ApprovalTargetType targetType,
            Long targetId
    );

    @Query("""
            SELECT request
            FROM ApprovalRequest request
            WHERE request.status = :status
              AND request.targetType IN :targetTypes
              AND request.submittedBy <> :userId
              AND request.targetCreatedBy <> :userId
            ORDER BY request.submittedAt ASC
            """)
    Page<ApprovalRequest> findPendingForUser(
            @Param("status") ApprovalStatus status,
            @Param("targetTypes") Set<ApprovalTargetType> targetTypes,
            @Param("userId") Long userId,
            Pageable pageable
    );
}
