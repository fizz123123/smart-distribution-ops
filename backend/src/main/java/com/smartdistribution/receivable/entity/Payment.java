package com.smartdistribution.receivable.entity;

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
 * 收款表頭：一筆代表客戶一次實際付款，分配到同一客戶的一筆或多筆應收
 * 沒有草稿，建立就是確認；登錯了由財務主管整筆作廢後重新登錄，不直接修改
 */
@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_number", unique = true, length = 20)
    private String paymentNumber;

    //前端每次收款操作產生一個；網路重試時用它認出是同一筆，不會重複入帳
    @Column(name = "client_request_id", nullable = false, unique = true, length = 64)
    private String clientRequestId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    //含稅，整數元
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    //匯款帳號後五碼、支票號碼等，選填
    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    //作廢時寫入
    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Payment(
            String clientRequestId,
            Long customerId,
            LocalDate receivedDate,
            BigDecimal amount,
            PaymentMethod method,
            String referenceNumber,
            Long createdBy
    ) {
        this.clientRequestId = clientRequestId;
        this.customerId = customerId;
        this.receivedDate = receivedDate;
        this.amount = amount;
        this.method = method;
        this.referenceNumber = referenceNumber == null || referenceNumber.isBlank() ? null : referenceNumber.strip();
        this.createdBy = createdBy;
        this.status = PaymentStatus.CONFIRMED;
    }

    public void assignNumber() {
        this.paymentNumber = "PAY-%06d".formatted(id);
    }

    //作廢後核銷失效，相關應收的未收餘額恢復；作廢的不能再作廢
    public void voidPayment(String reason, Long actorId, Instant at) {
        if (status != PaymentStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.PAYMENT_STATUS_CONFLICT);
        }

        this.voidReason = reason.strip();
        this.voidedBy = actorId;
        this.voidedAt = at;
        this.status = PaymentStatus.VOIDED;
    }
}
