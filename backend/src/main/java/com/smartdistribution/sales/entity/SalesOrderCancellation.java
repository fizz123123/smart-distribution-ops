package com.smartdistribution.sales.entity;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * 未出貨餘量取消申請，一張已核准的 SO 可以有多張取消申請，
 * 但同一時間只能有一張還沒結束（草稿、待核准或退回）
 */
@Getter
@Entity
@Table(name = "sales_order_cancellation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrderCancellation extends TimestampedEntity {

    //還沒結束的申請；同一張 SO 同時只能有一張
    public static final Set<SalesOrderCancellationStatus> OPEN_STATUSES = EnumSet.of(
            SalesOrderCancellationStatus.DRAFT,
            SalesOrderCancellationStatus.PENDING_APPROVAL,
            SalesOrderCancellationStatus.RETURNED
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cancellation_number", unique = true, length = 20)
    private String cancellationNumber;

    //同一模組用關聯，資料庫有外鍵
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderCancellationStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    //明細存在另一張表，整批取代時更新這個欄位，讓表頭的版本跟著加 1（同 SO）
    @Column(name = "lines_updated_at")
    private Instant linesUpdatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public SalesOrderCancellation(SalesOrder salesOrder, String reason, Long createdBy) {
        this.salesOrder = salesOrder;
        this.reason = reason.strip();
        this.createdBy = createdBy;
        this.status = SalesOrderCancellationStatus.DRAFT;
    }

    public void assignNumber() {
        this.cancellationNumber = "SOC-%06d".formatted(id);
    }

    public boolean isDraftOrReturned() {
        return status == SalesOrderCancellationStatus.DRAFT || status == SalesOrderCancellationStatus.RETURNED;
    }

    public void updateDraft(String reason, Instant linesUpdatedAt) {
        ensureDraftOrReturned();

        this.reason = reason.strip();
        this.linesUpdatedAt = linesUpdatedAt;
    }

    public void submit() {
        ensureDraftOrReturned();

        this.status = SalesOrderCancellationStatus.PENDING_APPROVAL;
    }

    public void approve(Instant approvedAt) {
        ensureStatus(SalesOrderCancellationStatus.PENDING_APPROVAL);

        this.approvedAt = approvedAt;
        this.status = SalesOrderCancellationStatus.APPROVED;
    }

    public void returnForRevision() {
        ensureStatus(SalesOrderCancellationStatus.PENDING_APPROVAL);

        this.status = SalesOrderCancellationStatus.RETURNED;
    }

    //撤回不想送出的申請；待核准中的申請要先被退回才能撤回
    public void withdraw() {
        ensureDraftOrReturned();

        this.status = SalesOrderCancellationStatus.WITHDRAWN;
    }

    private void ensureDraftOrReturned() {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.SO_CANCELLATION_STATUS_CONFLICT);
        }
    }

    private void ensureStatus(SalesOrderCancellationStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.SO_CANCELLATION_STATUS_CONFLICT);
        }
    }
}
