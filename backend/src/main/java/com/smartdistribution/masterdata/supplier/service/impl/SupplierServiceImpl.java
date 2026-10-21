package com.smartdistribution.masterdata.supplier.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.supplier.dto.request.ChangeSupplierStatusRequest;
import com.smartdistribution.masterdata.supplier.dto.request.CreateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.request.SubmitSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.request.UpdateSupplierRequest;
import com.smartdistribution.masterdata.supplier.dto.response.SupplierResponse;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.masterdata.supplier.mapper.SupplierMapper;
import com.smartdistribution.masterdata.supplier.repository.SupplierRepository;
import com.smartdistribution.masterdata.supplier.service.SupplierService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final SupplierMapper supplierMapper;

    @Override
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public PageResponse<SupplierResponse> findSuppliers(
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    ) {
        Page<SupplierResponse> suppliers = supplierRepository
                .searchByKeywordAndStatus(
                        Supplier.normalizeOptional(keyword),
                        status,
                        pageable
                ).map(supplierMapper::toResponse);

        return PageResponse.from(suppliers);
    }

    @Override
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public SupplierResponse findSupplier(Long supplierId) {
        return supplierMapper.toResponse(findSupplierById(supplierId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SUPPLIER_CREATE')")
    public SupplierResponse createSupplier(CreateSupplierRequest request) {

        String taxId = Supplier.normalizeOptional(request.taxId());

        if (taxId != null && supplierRepository.existsByTaxId(taxId)) {
            throw new BusinessException(ErrorCode.MASTER_DATA_TAX_ID_ALREADY_EXISTS);
        }

        Supplier supplier = new Supplier(
                request.name(),
                request.taxId(),
                request.contactName(),
                request.phone(),
                request.email(),
                request.address(),
                currentUserProvider.getCurrentUserId()
        );

        supplierRepository.saveAndFlush(supplier);

        //供應商編碼基於 id 產生，所以要先 insert 取得 id 再回填
        supplier.assignCode();
        supplierRepository.flush();

        return supplierMapper.toResponse(supplier);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SUPPLIER_UPDATE')")
    public SupplierResponse updateSupplier(
            Long supplierId,
            UpdateSupplierRequest request
    ) {
        Supplier supplier = findSupplierById(supplierId);
        verifyVersion(supplier, request.version());

        if (supplier.isDraftOrReturned()) {
            // 只有統編真的變更時才檢查；沒變的話查到的會是自己這一筆
            String newTaxId = Supplier.normalizeOptional(request.taxId());
            if (newTaxId != null
                    && !newTaxId.equals(supplier.getTaxId())
                    && supplierRepository.existsByTaxId(newTaxId)) {
                throw new BusinessException(ErrorCode.MASTER_DATA_TAX_ID_ALREADY_EXISTS);
            }

            supplier.updateDraft(
                    request.name(),
                    request.taxId(),
                    request.contactName(),
                    request.phone(),
                    request.email(),
                    request.address()
            );
            supplierRepository.flush();

            return supplierMapper.toResponse(supplier);
        }

        if (supplier.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        if (supplier.isIdentityChanged(request.name(), request.taxId())) {
            throw new BusinessException(ErrorCode.MASTER_DATA_IDENTITY_LOCKED);
        }

        String beforeData = contactSnapshot(supplier);
        supplier.updateContact(
                request.contactName(),
                request.phone(),
                request.email(),
                request.address()
        );
        supplierRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.SUPPLIER_UPDATE,
                supplierId,
                "修改供應商聯絡資料",
                beforeData,
                contactSnapshot(supplier),
                null
        );

        return supplierMapper.toResponse(supplier);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SUPPLIER_SUBMIT')")
    public SupplierResponse submitSupplier(
            Long supplierId,
            SubmitSupplierRequest request
    ) {
        Supplier supplier = findSupplierById(supplierId);
        verifyVersion(supplier, request.version());

        supplier.submit();
        approvalService.submit(
                ApprovalTargetType.SUPPLIER,
                supplierId,
                currentUserProvider.getCurrentUserId()
        );
        supplierRepository.flush();

        return supplierMapper.toResponse(supplier);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SUPPLIER_STATUS_MANAGE')")
    public SupplierResponse deactivateSupplier(
            Long supplierId,
            ChangeSupplierStatusRequest request
    ) {
        Supplier supplier = findSupplierById(supplierId);
        verifyVersion(supplier, request.version());

        supplier.deactivate();
        supplierRepository.flush();

        recordStatusChange(
                supplier,
                AuditActionType.SUPPLIER_DEACTIVATE,
                "停用供應商",
                MasterDataStatus.ACTIVE,
                request.reason()
        );

        return supplierMapper.toResponse(supplier);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SUPPLIER_STATUS_MANAGE')")
    public SupplierResponse activateSupplier(
            Long supplierId,
            ChangeSupplierStatusRequest request
    ) {
        Supplier supplier = findSupplierById(supplierId);
        verifyVersion(supplier, request.version());

        supplier.activate();
        supplierRepository.flush();

        recordStatusChange(
                supplier,
                AuditActionType.SUPPLIER_ACTIVATE,
                "重新啟用供應商",
                MasterDataStatus.INACTIVE,
                request.reason()
        );

        return supplierMapper.toResponse(supplier);
    }

    private Supplier findSupplierById(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND));
    }

    private void verifyVersion(Supplier supplier, Long expectedVersion) {
        if (!Objects.equals(supplier.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void recordStatusChange(
            Supplier supplier,
            AuditActionType actionType,
            String summary,
            MasterDataStatus beforeStatus,
            String reason
    ) {
        auditService.record(
                currentUserProvider.getCurrentUserId(),
                actionType,
                supplier.getId(),
                summary,
                "status=" + beforeStatus,
                "status=" + supplier.getStatus(),
                reason.strip()
        );
    }

    private String contactSnapshot(Supplier supplier) {
        return "contactName=" + supplier.getContactName()
                + ", phone=" + supplier.getPhone()
                + ", email=" + supplier.getEmail()
                + ", address=" + supplier.getAddress();
    }
}
