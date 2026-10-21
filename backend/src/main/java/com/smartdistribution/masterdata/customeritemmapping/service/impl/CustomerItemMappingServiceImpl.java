package com.smartdistribution.masterdata.customeritemmapping.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.ChangeCustomerItemMappingStatusRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.CreateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.SubmitCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.request.UpdateCustomerItemMappingRequest;
import com.smartdistribution.masterdata.customeritemmapping.dto.response.CustomerItemMappingResponse;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.mapper.CustomerItemMappingMapper;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.masterdata.customeritemmapping.service.CustomerItemMappingService;
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
public class CustomerItemMappingServiceImpl implements CustomerItemMappingService {

    private final CustomerItemMappingRepository customerItemMappingRepository;
    private final CustomerRepository customerRepository;
    private final CompanyItemRepository companyItemRepository;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerItemMappingMapper customerItemMappingMapper;

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_VIEW')")
    public PageResponse<CustomerItemMappingResponse> findMappings(
            Long customerId,
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();

        Page<CustomerItemMappingResponse> mappings = customerItemMappingRepository
                .searchByCustomerAndKeywordAndStatus(customerId, normalizedKeyword, status, pageable)
                .map(customerItemMappingMapper::toResponse);

        return PageResponse.from(mappings);
    }

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_VIEW')")
    public CustomerItemMappingResponse findMapping(Long mappingId) {
        return customerItemMappingMapper.toResponse(findMappingById(mappingId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_CREATE')")
    public CustomerItemMappingResponse createMapping(CreateCustomerItemMappingRequest request) {

        Customer customer = findActiveCustomer(request.customerId());

        CompanyItem companyItem = findActiveCompanyItem(request.companyItemId());

        //未填客戶料號時以公司料號代入，再檢查 "客戶＋客戶料號" 是否重複
        String customerItemCode =
                CustomerItemMapping.resolveCustomerItemCode(request.customerItemCode(), companyItem);

        if (customerItemMappingRepository.existsByCustomerIdAndCustomerItemCode(
                customer.getId(),
                customerItemCode
        )) {
            throw new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_ALREADY_EXISTS);
        }

        CustomerItemMapping mapping = new CustomerItemMapping(
                customer,
                request.customerItemCode(),
                companyItem,
                currentUserProvider.getCurrentUserId()
        );
        customerItemMappingRepository.saveAndFlush(mapping);

        return customerItemMappingMapper.toResponse(mapping);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_UPDATE')")
    public CustomerItemMappingResponse updateMapping(
            Long mappingId,
            UpdateCustomerItemMappingRequest request
    ) {
        CustomerItemMapping mapping = findMappingById(mappingId);
        verifyVersion(mapping, request.version());

        if (mapping.getStatus() == MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_IDENTITY_LOCKED);
        }

        if (!mapping.isDraftOrReturned()) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        Customer customer = findActiveCustomer(request.customerId());
        CompanyItem companyItem = findActiveCompanyItem(request.companyItemId());

        String customerItemCode =
                CustomerItemMapping.resolveCustomerItemCode(request.customerItemCode(), companyItem);

        boolean keyChanged = !customer.getId().equals(mapping.getCustomer().getId())
                || !customerItemCode.equalsIgnoreCase(mapping.getCustomerItemCode());

        if (keyChanged && customerItemMappingRepository.existsByCustomerIdAndCustomerItemCode(
                customer.getId(),
                customerItemCode
        )) {
            throw new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_ALREADY_EXISTS);
        }

        mapping.updateDraft(customer, request.customerItemCode(), companyItem);
        customerItemMappingRepository.flush();

        return customerItemMappingMapper.toResponse(mapping);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_SUBMIT')")
    public CustomerItemMappingResponse submitMapping(
            Long mappingId,
            SubmitCustomerItemMappingRequest request
    ) {
        CustomerItemMapping mapping = findMappingById(mappingId);
        verifyVersion(mapping, request.version());
        ensureReferencesActive(mapping);

        mapping.submit();
        approvalService.submit(
                ApprovalTargetType.CUSTOMER_ITEM_MAPPING,
                mappingId,
                currentUserProvider.getCurrentUserId()
        );
        customerItemMappingRepository.flush();

        return customerItemMappingMapper.toResponse(mapping);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_STATUS_MANAGE')")
    public CustomerItemMappingResponse deactivateMapping(
            Long mappingId,
            ChangeCustomerItemMappingStatusRequest request
    ) {
        CustomerItemMapping mapping = findMappingById(mappingId);
        verifyVersion(mapping, request.version());

        mapping.deactivate();
        customerItemMappingRepository.flush();

        recordStatusChange(
                mapping,
                AuditActionType.CUSTOMER_ITEM_MAPPING_DEACTIVATE,
                "停用客戶料號對應",
                MasterDataStatus.ACTIVE,
                request.reason()
        );

        return customerItemMappingMapper.toResponse(mapping);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_ITEM_MAPPING_STATUS_MANAGE')")
    public CustomerItemMappingResponse activateMapping(
            Long mappingId,
            ChangeCustomerItemMappingStatusRequest request
    ) {
        CustomerItemMapping mapping = findMappingById(mappingId);
        verifyVersion(mapping, request.version());

        //重新啟用前確認必要關聯仍有效
        ensureReferencesActive(mapping);

        mapping.activate();
        customerItemMappingRepository.flush();

        recordStatusChange(
                mapping,
                AuditActionType.CUSTOMER_ITEM_MAPPING_ACTIVATE,
                "重新啟用客戶料號對應",
                MasterDataStatus.INACTIVE,
                request.reason()
        );

        return customerItemMappingMapper.toResponse(mapping);
    }

    private CustomerItemMapping findMappingById(Long mappingId) {
        return customerItemMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_NOT_FOUND));
    }

    private Customer findActiveCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || customer.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE);
        }

        return customer;
    }

    private CompanyItem findActiveCompanyItem(Long companyItemId) {
        CompanyItem companyItem = companyItemRepository.findById(companyItemId).orElse(null);
        if (companyItem == null || companyItem.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE);
        }

        return companyItem;
    }

    private void ensureReferencesActive(CustomerItemMapping mapping) {
        if (!mapping.hasActiveReferences()) {
            throw new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE);
        }
    }

    private void verifyVersion(CustomerItemMapping mapping, Long expectedVersion) {
        if (!Objects.equals(mapping.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void recordStatusChange(
            CustomerItemMapping mapping,
            AuditActionType actionType,
            String summary,
            MasterDataStatus beforeStatus,
            String reason
    ) {
        auditService.record(
                currentUserProvider.getCurrentUserId(),
                actionType,
                mapping.getId(),
                summary,
                "status=" + beforeStatus,
                "status=" + mapping.getStatus(),
                reason.strip()
        );
    }
}
