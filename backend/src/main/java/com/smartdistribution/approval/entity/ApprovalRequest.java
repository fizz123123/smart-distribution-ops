package com.smartdistribution.approval.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "approval_request",
        indexes = {
                @Index(
                        name = "idx_approval_request_target",
                        columnList = "target_type,target_id"
                ),
                @Index(
                        name = "idx_approval_request_pending",
                        columnList = "status,target_type"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private ApprovalTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "target_label", nullable = false, length = 200)
    private String targetLabel;

    @Column(name = "target_created_by", nullable = false)
    private Long targetCreatedBy;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    public ApprovalRequest(
            ApprovalTargetType targetType,
            Long targetId,
            int roundNo,
            String targetLabel,
            Long targetCreatedBy,
            Long submittedBy,
            Instant submittedAt
    ) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.roundNo = roundNo;
        this.targetLabel = targetLabel;
        this.targetCreatedBy = targetCreatedBy;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.status = ApprovalStatus.PENDING;
    }

    public void approve() {
        this.status = ApprovalStatus.APPROVED;
    }

    public void returnForRevision() {
        this.status = ApprovalStatus.RETURNED;
    }
}
