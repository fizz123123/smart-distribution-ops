package com.smartdistribution.masterdata.supplier.entity;

import com.smartdistribution.masterdata.common.MasterDataStatus;
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

import java.util.Objects;

@Getter
@Entity
@Table(name = "supplier")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", unique = true, length = 20)
    private String supplierCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "tax_id", unique = true, length = 8)
    private String taxId;

    @Column(name = "contact_name", length = 50)
    private String contactName;

    @Column(length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterDataStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public Supplier(
            String name,
            String taxId,
            String contactName,
            String phone,
            String email,
            String address,
            Long createdBy
    ) {
        this.name = name.strip();
        this.taxId = normalizeOptional(taxId);
        this.contactName = normalizeOptional(contactName);
        this.phone = normalizeOptional(phone);
        this.email = normalizeOptional(email);
        this.address = normalizeOptional(address);
        this.createdBy = createdBy;
        this.status = MasterDataStatus.DRAFT;
    }

    public void assignCode() {
        this.supplierCode = "SUP-%06d".formatted(id);
    }

    public boolean isDraftOrReturned() {
        return status == MasterDataStatus.DRAFT || status == MasterDataStatus.RETURNED;
    }

    public boolean isIdentityChanged(String name, String taxId) {
        return !this.name.equals(name.strip()) ||
                !Objects.equals(this.taxId, normalizeOptional(taxId));
    }

    public void updateDraft(
            String name,
            String taxId,
            String contactName,
            String phone,
            String email,
            String address
    ) {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.name = name.strip();
        this.taxId = normalizeOptional(taxId);
        this.contactName = normalizeOptional(contactName);
        this.phone = normalizeOptional(phone);
        this.email = normalizeOptional(email);
        this.address = normalizeOptional(address);
    }

    public void updateContact(
            String contactName,
            String phone,
            String email,
            String address
    ) {
        if (status != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.contactName = normalizeOptional(contactName);
        this.phone = normalizeOptional(phone);
        this.email = normalizeOptional(email);
        this.address = normalizeOptional(address);
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

    public static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }
}
