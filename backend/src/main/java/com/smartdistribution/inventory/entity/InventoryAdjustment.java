package com.smartdistribution.inventory.entity;

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

import java.time.Instant;

/**
 * 庫存調整單表頭：建立即過帳，不另走核准；
 * 過帳後不修改，登錄錯誤時建立一張相反方向的調整並把原調整標記為 REVERSED
 */
@Getter
@Entity
@Table(name = "inventory_adjustment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryAdjustment extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_number", unique = true, length = 20)
    private String adjustmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private AdjustmentReasonType reasonType;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdjustmentStatus status;

    //這張調整單如果是用於反向更正，指向被更正的原調整單
    @Column(name = "reversal_of_id", unique = true)
    private Long reversalOfId;

    @Column(name = "posted_by", nullable = false)
    private Long postedBy;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryAdjustment(
            AdjustmentReasonType reasonType,
            String description,
            Long reversalOfId,
            Long postedBy,
            Instant postedAt
    ) {
        this.reasonType = reasonType;
        this.description = description.strip();
        this.reversalOfId = reversalOfId;
        this.postedBy = postedBy;
        this.postedAt = postedAt;
        this.status = AdjustmentStatus.POSTED;
    }

    public void assignNumber() {
        this.adjustmentNumber = "ADJ-%06d".formatted(id);
    }

    public boolean isReversal() {
        return reversalOfId != null;
    }

    public InventoryTransactionType transactionType() {
        if (isReversal()) {
            return InventoryTransactionType.ADJUSTMENT_REVERSAL;
        }

        if (reasonType == AdjustmentReasonType.OPENING_BALANCE) {
            return InventoryTransactionType.OPENING_BALANCE;
        }

        return InventoryTransactionType.ADJUSTMENT;
    }

    //只能更正一次，更正紀錄本身也不能再被更正，需要時另建一張新的調整
    public void markReversed() {
        if (status != AdjustmentStatus.POSTED || isReversal()) {
            throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_NOT_REVERSIBLE);
        }

        this.status = AdjustmentStatus.REVERSED;
    }
}
