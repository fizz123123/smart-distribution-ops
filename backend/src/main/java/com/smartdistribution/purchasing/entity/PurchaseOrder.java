package com.smartdistribution.purchasing.entity;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * PO 表頭：供應商屬於主檔模組，只存 ID
 * 只保存未稅金額；稅額與含稅總額只在 PDF 上依 5% 算出來顯示
 */
@Getter
@Entity
@Table(name = "purchase_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", unique = true, length = 20)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status;

    @Column(name = "expected_arrival_date")
    private LocalDate expectedArrivalDate;

    @Column(length = 500)
    private String remark;

    //送審時寫入：明細小計加總後四捨五入到元
    @Column(name = "untaxed_amount", precision = 15, scale = 0)
    private BigDecimal untaxedAmount;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    //取消已核准 PO 時寫入
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    //短收結案時寫入；全部收齊而結案的 PO 這三個欄位是空的
    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    //明細存在另一張表，整批取代時更新這個欄位，讓表頭的版本跟著加 1
    @Column(name = "lines_updated_at")
    private Instant linesUpdatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public PurchaseOrder(Long supplierId, Long createdBy, LocalDate expectedArrivalDate, String remark) {
        this.supplierId = supplierId;
        this.createdBy = createdBy;
        this.status = PurchaseOrderStatus.DRAFT;
        applyDraftFields(expectedArrivalDate, remark);
    }

    public void assignNumber() {
        this.poNumber = "PO-%06d".formatted(id);
    }

    public boolean isDraftOrReturned() {
        return status == PurchaseOrderStatus.DRAFT || status == PurchaseOrderStatus.RETURNED;
    }

    public void updateDraft(Long supplierId, LocalDate expectedArrivalDate, String remark, Instant linesUpdatedAt) {
        ensureDraftOrReturned();

        this.supplierId = supplierId;
        this.linesUpdatedAt = linesUpdatedAt;
        this.untaxedAmount = null; //退回後修改，上次送審的金額已經不準，等下次送審再重算
        applyDraftFields(expectedArrivalDate, remark);
    }

    public void submit(BigDecimal untaxedAmount, Instant submittedAt) {
        ensureDraftOrReturned();

        this.untaxedAmount = untaxedAmount;
        this.submittedAt = submittedAt;
        this.status = PurchaseOrderStatus.PENDING_APPROVAL;
    }

    public void approve(Instant approvedAt) {
        ensureStatus(PurchaseOrderStatus.PENDING_APPROVAL);

        this.approvedAt = approvedAt;
        this.status = PurchaseOrderStatus.APPROVED;
    }

    public void returnForRevision() {
        ensureStatus(PurchaseOrderStatus.PENDING_APPROVAL);

        this.status = PurchaseOrderStatus.RETURNED;
    }

    public boolean canReceive() {
        return status == PurchaseOrderStatus.APPROVED || status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    /**
     * 收貨過帳後更新：所有明細都不再有未結量時結案，否則為部分收貨
     *
     * @param allLinesDone 每筆明細都已完成、短收結案或取消
     */
    public void recordReceipt(boolean allLinesDone) {
        if (!canReceive()) {
            throw new BusinessException(ErrorCode.PO_STATUS_CONFLICT);
        }

        this.status = allLinesDone ? PurchaseOrderStatus.CLOSED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    /**
     * 取消已核准、還沒有任何收貨的 PO；有收貨的要改用短收結案
     */
    public void cancelApproved(Long actorId, String reason, Instant cancelledAt) {
        if (status == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException(ErrorCode.PO_HAS_RECEIPTS);
        }
        ensureStatus(PurchaseOrderStatus.APPROVED);

        this.cancelReason = reason.strip();
        this.cancelledBy = actorId;
        this.cancelledAt = cancelledAt;
        this.status = PurchaseOrderStatus.CANCELLED;
    }

    /**
     * 短收結案：已部分收貨的 PO，剩下的數量不再等，直接結案
     */
    public void closeShort(Long actorId, String reason, Instant closedAt) {
        ensureStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);

        this.closeReason = reason.strip();
        this.closedBy = actorId;
        this.closedAt = closedAt;
        this.status = PurchaseOrderStatus.CLOSED;
    }

    //放棄草稿；已送審的 PO 要先被退回才能放棄
    public void cancelDraft() {
        ensureDraftOrReturned();

        this.status = PurchaseOrderStatus.CANCELLED;
    }

    private void ensureDraftOrReturned() {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.PO_STATUS_CONFLICT);
        }
    }

    private void ensureStatus(PurchaseOrderStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.PO_STATUS_CONFLICT);
        }
    }

    //建立與修改共用：草稿可以自由編輯的欄位
    private void applyDraftFields(LocalDate expectedArrivalDate, String remark) {
        this.expectedArrivalDate = expectedArrivalDate;
        this.remark = normalizeOptional(remark);
    }

    //空白或只有空格的備註視為沒填，有內容就去掉前後空白
    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
