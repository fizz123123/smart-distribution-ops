package com.smartdistribution.pricing.entity;

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
import java.time.LocalDate;

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

    //待覆核時是申請的生效日，null 代表核准當天；核准後一定有值
    //資料庫允許 null 是為了讓既有資料補值
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    //null 代表不限期，直到被下一個版本接手
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceStatus status;

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
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Long createdBy
    ) {
        this.customerItemMapping = customerItemMapping;
        this.pricingUnit = pricingUnit;
        this.unitPriceExcludingTax = unitPriceExcludingTax;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.createdBy = createdBy;
        this.status = PriceStatus.PENDING_REVIEW;
    }

    /**
     * 核准並確定生效日
     *
     * @param reviewerId 核准者
     * @param reviewedAt 核准時間
     * @param effectiveFrom 實際生效日，由核准流程依申請的生效日與核准當天決定
     */
    public void approve(Long reviewerId, Instant reviewedAt, LocalDate effectiveFrom) {
        if (status != PriceStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.PRICE_STATUS_CONFLICT);
        }

        this.effectiveFrom = effectiveFrom;
        this.status = PriceStatus.ACTIVE;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
    }

    //價格覆核的退回即為拒絕，拒絕後不能重新送審，須另建新版本
    public void reject(Long reviewerId, Instant reviewedAt) {
        if (status != PriceStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.PRICE_STATUS_CONFLICT);
        }

        this.status = PriceStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
    }

    /**
     * 下一個版本核准時呼叫：本版本最晚只到下一版生效日的前一天，並轉為 REPLACED
     * 本來就更早結束的，截止日不變
     *
     * @param nextEffectiveFrom 下一個版本的生效日
     */
    public void endBefore(LocalDate nextEffectiveFrom) {
        if (!PriceStatus.APPROVED_VERSIONS.contains(status)) {
            throw new BusinessException(ErrorCode.PRICE_STATUS_CONFLICT);
        }

        LocalDate lastDay = nextEffectiveFrom.minusDays(1);
        if (effectiveTo == null || effectiveTo.isAfter(lastDay)) {
            this.effectiveTo = lastDay;
        }
        this.status = PriceStatus.REPLACED;
    }

    /**
     * 截止日已經過了；過期只看日期，不改狀態
     *
     * @param today 今天
     * @return 已過期時為 true
     */
    public boolean isExpiredOn(LocalDate today) {
        return effectiveTo != null && effectiveTo.isBefore(today);
    }

    //撤銷代表價格有誤；被取代但還沒到期的版本也能撤銷，空出的期間不會自動接回前一版
    public void revoke(Long revokerId, String reason, Instant revokedAt) {
        if (!PriceStatus.APPROVED_VERSIONS.contains(status)) {
            throw new BusinessException(ErrorCode.PRICE_STATUS_CONFLICT);
        }

        this.status = PriceStatus.REVOKED;
        this.revokedBy = revokerId;
        this.revokeReason = reason.strip();
        this.revokedAt = revokedAt;
    }
}
