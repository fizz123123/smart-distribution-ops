package com.smartdistribution.masterdata.customerprice.entity;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
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

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "customer_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPrice extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_item_mapping_id", nullable = false)
    private CustomerItemMapping customerItemMapping;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_unit_code", nullable = false, length = 10)
    private UnitOfMeasure pricingUnit;

    //未稅 TWD 單價，保存至小數 2 位
    @Column(name = "unit_price_excluding_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceExcludingTax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerPriceStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    //覆核（核准或拒絕）者與時間，價格歷史直接顯示
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;

    @Version
    @Column(nullable = false)
    private Long version;

    public CustomerPrice(
            CustomerItemMapping customerItemMapping,
            UnitOfMeasure pricingUnit,
            BigDecimal unitPriceExcludingTax,
            Long createdBy
    ) {
        this.customerItemMapping = customerItemMapping;
        this.pricingUnit = pricingUnit;
        this.unitPriceExcludingTax = unitPriceExcludingTax;
        this.createdBy = createdBy;
        this.status = CustomerPriceStatus.PENDING_REVIEW;
    }

    public void approve(Long reviewerId, Instant reviewedAt) {
        if (status != CustomerPriceStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = CustomerPriceStatus.ACTIVE;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
    }

    //價格覆核的退回即為拒絕，拒絕後不能重新送審，須另建新版本
    public void reject(Long reviewerId, Instant reviewedAt) {
        if (status != CustomerPriceStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = CustomerPriceStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
    }

    //新版本核准時，同一對應與計價單位的舊 ACTIVE 價格轉為 REPLACED
    public void replace() {
        if (status != CustomerPriceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = CustomerPriceStatus.REPLACED;
    }

    //價格撤銷後不得供新的 SO 送審
    public void revoke(Long revokerId, String reason, Instant revokedAt) {
        if (status != CustomerPriceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = CustomerPriceStatus.REVOKED;
        this.revokedBy = revokerId;
        this.revokeReason = reason.strip();
        this.revokedAt = revokedAt;
    }
}
