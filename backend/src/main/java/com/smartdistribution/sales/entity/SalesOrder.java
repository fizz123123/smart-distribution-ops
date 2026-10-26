package com.smartdistribution.sales.entity;

import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
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
 * SO 表頭：客戶與負責業務屬於其他模組，只存 ID
 * 付款條件與金額是送審快照：送審時寫入，草稿為 null，主檔之後的變更不改寫
 */
@Getter
@Entity
@Table(name = "sales_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrder extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "so_number", unique = true, length = 20)
    private String soNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_order_number", length = 50)
    private String customerOrderNumber;

    //資料歸屬與儀表板依業務統計使用，和建立者分開保存
    @Column(name = "responsible_sales_user_id", nullable = false)
    private Long responsibleSalesUserId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_term", length = 10)
    private PaymentTerm paymentTerm;

    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", length = 20)
    private DeliveryMethod deliveryMethod;

    @Column(name = "delivery_address", length = 200)
    private String deliveryAddress;

    @Column(name = "contact_name", length = 50)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(length = 500)
    private String remark;

    @Column(name = "untaxed_amount", precision = 15, scale = 0)
    private BigDecimal untaxedAmount;

    @Column(name = "tax_amount", precision = 15, scale = 0)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 15, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    //SO明細存在另一張表，整批取代時更新這個欄位，讓表頭的版本跟著加 1
    @Column(name = "lines_updated_at")
    private Instant linesUpdatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public SalesOrder(
            Long customerId,
            Long responsibleSalesUserId,
            Long createdBy,
            SalesOrderChannel channel,
            DraftFields fields
    ) {
        this.customerId = customerId;
        this.responsibleSalesUserId = responsibleSalesUserId;
        this.createdBy = createdBy;
        this.channel = channel;
        this.status = SalesOrderStatus.DRAFT;
        applyDraftFields(fields);
    }

    public void assignNumber() {
        this.soNumber = "SO-%06d".formatted(id);
    }

    public boolean isDraftOrReturned() {
        return status == SalesOrderStatus.DRAFT || status == SalesOrderStatus.RETURNED;
    }

    public void updateDraft(
            Long customerId,
            Long responsibleSalesUserId,
            DraftFields fields,
            Instant linesUpdatedAt
    ) {
        ensureDraftOrReturned();

        this.customerId = customerId;
        this.responsibleSalesUserId = responsibleSalesUserId;
        this.linesUpdatedAt = linesUpdatedAt;
        applyDraftFields(fields);
    }

    //送審時保存付款條件與金額快照
    public void submit(PaymentTerm paymentTerm, Amounts amounts, Instant submittedAt) {
        ensureDraftOrReturned();

        this.paymentTerm = paymentTerm;
        this.untaxedAmount = amounts.untaxed();
        this.taxAmount = amounts.tax();
        this.totalAmount = amounts.total();
        this.submittedAt = submittedAt;
        this.status = SalesOrderStatus.PENDING_APPROVAL;
    }

    public void approve(Instant approvedAt) {
        ensureStatus(SalesOrderStatus.PENDING_APPROVAL);

        this.approvedAt = approvedAt;
        this.status = SalesOrderStatus.APPROVED;
    }

    public void returnForRevision() {
        ensureStatus(SalesOrderStatus.PENDING_APPROVAL);

        this.status = SalesOrderStatus.RETURNED;
    }

    // 履約結案：每筆明細的有效訂購量都已出貨或取消（P3 由餘量取消觸發，P5 再加上 DO 出貨）
    public void close() {
        ensureStatus(SalesOrderStatus.APPROVED);

        this.status = SalesOrderStatus.CLOSED;
    }

    //放棄草稿；已送審的 SO 要先被退回才能放棄
    public void cancelDraft() {
        ensureDraftOrReturned();

        this.status = SalesOrderStatus.CANCELLED;
    }

    private void ensureDraftOrReturned() {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.SO_STATUS_CONFLICT);
        }
    }

    private void ensureStatus(SalesOrderStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.SO_STATUS_CONFLICT);
        }
    }

    private void applyDraftFields(DraftFields fields) {
        this.customerOrderNumber = normalizeOptional(fields.customerOrderNumber());
        this.requestedDeliveryDate = fields.requestedDeliveryDate();
        this.deliveryMethod = fields.deliveryMethod();
        this.deliveryAddress = normalizeOptional(fields.deliveryAddress());
        this.contactName = normalizeOptional(fields.contactName());
        this.contactPhone = normalizeOptional(fields.contactPhone());
        this.remark = normalizeOptional(fields.remark());
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 草稿可以自由編輯的欄位，建立與修改共用
     */
    public record DraftFields(
            String customerOrderNumber,
            LocalDate requestedDeliveryDate,
            DeliveryMethod deliveryMethod,
            String deliveryAddress,
            String contactName,
            String contactPhone,
            String remark
    ) {
    }

    public record Amounts(
            BigDecimal untaxed,
            BigDecimal tax,
            BigDecimal total
    ) {
    }
}
