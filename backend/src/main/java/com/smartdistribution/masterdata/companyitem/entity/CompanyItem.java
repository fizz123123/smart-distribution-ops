package com.smartdistribution.masterdata.companyitem.entity;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
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
import java.util.Locale;

@Getter
@Entity
@Table(name = "company_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyItem extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", nullable = false, unique = true, length = 50)
    private String itemCode;

    //品名，包含材質與規格
    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit_code", nullable = false, length = 10)
    private UnitOfMeasure baseUnit;

    @Column(name = "preferred_supplier_id")
    private Long preferredSupplierId;

    //單位換算存在另一張表；異動時更新這個欄位，讓料號的版本跟著加 1
    @Column(name = "unit_conversions_updated_at")
    private Instant unitConversionsUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterDataStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public CompanyItem(
            String itemCode,
            String name,
            UnitOfMeasure baseUnit,
            Long preferredSupplierId,
            Long createdBy
    ) {
        this.itemCode = normalizeCode(itemCode);
        this.name = name.strip();
        this.baseUnit = baseUnit;
        this.preferredSupplierId = preferredSupplierId;
        this.createdBy = createdBy;
        this.status = MasterDataStatus.DRAFT;
    }

    public boolean isDraftOrReturned() {
        return status == MasterDataStatus.DRAFT || status == MasterDataStatus.RETURNED;
    }

    public boolean isIdentityChanged(String itemCode, String name, UnitOfMeasure baseUnit) {
        return !this.itemCode.equals(normalizeCode(itemCode))
                || !this.name.equals(name.strip())
                || this.baseUnit != baseUnit;
    }

    public void updateDraft(
            String itemCode,
            String name,
            UnitOfMeasure baseUnit,
            Long preferredSupplierId
    ) {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.itemCode = normalizeCode(itemCode);
        this.name = name.strip();
        this.baseUnit = baseUnit;
        this.preferredSupplierId = preferredSupplierId;
    }

    public void changePreferredSupplier(Long preferredSupplierId) {
        if (status != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.preferredSupplierId = preferredSupplierId;
    }

    //草稿與啟用中都可以改換算；待核准與停用期間不行
    public void markUnitConversionsChanged(Instant changedAt) {
        if (!isDraftOrReturned() && status != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.unitConversionsUpdatedAt = changedAt;
    }

    public void submit() {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = MasterDataStatus.PENDING_APPROVAL;
    }

    public void approve() {
        if (status != MasterDataStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = MasterDataStatus.ACTIVE;
    }

    public void returnForRevision() {
        if (status != MasterDataStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = MasterDataStatus.RETURNED;
    }

    public void deactivate() {
        if (status != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = MasterDataStatus.INACTIVE;
    }

    public void activate() {
        if (status != MasterDataStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.status = MasterDataStatus.ACTIVE;
    }

    /**
     * 去除前後空白並轉大寫，避免 pe-12 與 PE-12 被當成兩個料號
     */
    public static String normalizeCode(String itemCode) {
        return itemCode.strip().toUpperCase(Locale.ROOT);
    }
}
