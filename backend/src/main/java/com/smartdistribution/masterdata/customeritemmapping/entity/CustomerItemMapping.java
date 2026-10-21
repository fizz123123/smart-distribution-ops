package com.smartdistribution.masterdata.customeritemmapping.entity;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "customer_item_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_item_mapping_customer_code",
                columnNames = {"customer_id", "customer_item_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerItemMapping extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    //客戶料號；未填時以公司料號代入
    @Column(name = "customer_item_code", nullable = false, length = 50)
    private String customerItemCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_item_id", nullable = false)
    private CompanyItem companyItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterDataStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public CustomerItemMapping(
            Customer customer,
            String customerItemCode,
            CompanyItem companyItem,
            Long createdBy
    ) {
        this.customer = customer;
        this.customerItemCode = resolveCustomerItemCode(customerItemCode, companyItem);
        this.companyItem = companyItem;
        this.createdBy = createdBy;
        this.status = MasterDataStatus.DRAFT;
    }

    public boolean isDraftOrReturned() {
        return status == MasterDataStatus.DRAFT || status == MasterDataStatus.RETURNED;
    }

    //客戶與公司料號都必須是啟用狀態，Mapping關係才能建立、送審、核准或重新啟用
    public boolean hasActiveReferences() {
        return customer.getStatus() == MasterDataStatus.ACTIVE
                && companyItem.getStatus() == MasterDataStatus.ACTIVE;
    }

    public void updateDraft(Customer customer, String customerItemCode, CompanyItem companyItem) {
        if (!isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        this.customer = customer;
        this.customerItemCode = resolveCustomerItemCode(customerItemCode, companyItem);
        this.companyItem = companyItem;
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
     * 客戶沒有自己的料號時，以公司料號代入
     */
    public static String resolveCustomerItemCode(String customerItemCode, CompanyItem companyItem) {
        if (customerItemCode == null || customerItemCode.isBlank()) {
            return companyItem.getItemCode();
        }

        return customerItemCode.strip();
    }
}
