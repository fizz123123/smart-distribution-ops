package com.smartdistribution.masterdata.customer.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customer.dto.request.ChangeCustomerPaymentTermRequest;
import com.smartdistribution.masterdata.customer.dto.request.ChangeCustomerStatusRequest;
import com.smartdistribution.masterdata.customer.dto.request.CreateCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.request.SubmitCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.request.UpdateCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.response.CustomerResponse;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customer.mapper.CustomerMapper;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import com.smartdistribution.masterdata.customer.service.CustomerService;
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
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerMapper customerMapper;

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public PageResponse<CustomerResponse> findCustomers(
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    ) {
        Page<CustomerResponse> customers = customerRepository
                .searchByKeywordAndStatus(
                        Customer.normalizeOptional(keyword),
                        status,
                        pageable
                ).map(customerMapper::toResponse);

        return PageResponse.from(customers);
    }

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public CustomerResponse findCustomer(Long customerId) {
        return customerMapper.toResponse(findCustomerById(customerId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public CustomerResponse createCustomer(CreateCustomerRequest request) {

        String taxId = Customer.normalizeOptional(request.taxId());

        if (taxId != null && customerRepository.existsByTaxId(taxId)) {
            throw new BusinessException(ErrorCode.MASTER_DATA_TAX_ID_ALREADY_EXISTS);
        }

        Customer customer = new Customer(
                request.name(),
                request.taxId(),
                request.contactName(),
                request.phone(),
                request.email(),
                request.address(),
                currentUserProvider.getCurrentUserId()
        );

        customerRepository.saveAndFlush(customer);

        //客戶編碼基於 id 產生，所以要先 insert 取得 id 再回填
        customer.assignCode();
        customerRepository.flush();

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public CustomerResponse updateCustomer(
            Long customerId,
            UpdateCustomerRequest request
    ) {
        Customer customer = findCustomerById(customerId);
        verifyVersion(customer, request.version());

        if (customer.isDraftOrReturned()) {
            String newTaxId = Customer.normalizeOptional(request.taxId());
            if (newTaxId != null
                    && !newTaxId.equals(customer.getTaxId())
                    && customerRepository.existsByTaxId(newTaxId)) {
                throw new BusinessException(ErrorCode.MASTER_DATA_TAX_ID_ALREADY_EXISTS);
            }

            customer.updateDraft(
                    request.name(),
                    request.taxId(),
                    request.contactName(),
                    request.phone(),
                    request.email(),
                    request.address()
            );
            customerRepository.flush();

            return customerMapper.toResponse(customer);
        }

        if (customer.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        if (customer.isIdentityChanged(request.name(), request.taxId())) {
            throw new BusinessException(ErrorCode.MASTER_DATA_IDENTITY_LOCKED);
        }

        String beforeData = contactSnapshot(customer);
        customer.updateContact(
                request.contactName(),
                request.phone(),
                request.email(),
                request.address()
        );
        customerRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.CUSTOMER_UPDATE,
                customerId,
                "修改客戶聯絡資料",
                beforeData,
                contactSnapshot(customer),
                null
        );

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_SUBMIT')")
    public CustomerResponse submitCustomer(
            Long customerId,
            SubmitCustomerRequest request
    ) {
        Customer customer = findCustomerById(customerId);
        verifyVersion(customer, request.version());

        customer.submit();
        approvalService.submit(
                ApprovalTargetType.CUSTOMER,
                customerId,
                currentUserProvider.getCurrentUserId()
        );
        customerRepository.flush();

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_STATUS_MANAGE')")
    public CustomerResponse deactivateCustomer(
            Long customerId,
            ChangeCustomerStatusRequest request
    ) {
        Customer customer = findCustomerById(customerId);
        verifyVersion(customer, request.version());

        customer.deactivate();
        customerRepository.flush();

        recordStatusChange(
                customer,
                AuditActionType.CUSTOMER_DEACTIVATE,
                "停用客戶",
                MasterDataStatus.ACTIVE,
                request.reason()
        );

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_STATUS_MANAGE')")
    public CustomerResponse activateCustomer(
            Long customerId,
            ChangeCustomerStatusRequest request
    ) {
        Customer customer = findCustomerById(customerId);
        verifyVersion(customer, request.version());

        customer.activate();
        customerRepository.flush();

        recordStatusChange(
                customer,
                AuditActionType.CUSTOMER_ACTIVATE,
                "重新啟用客戶",
                MasterDataStatus.INACTIVE,
                request.reason()
        );

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_PAYMENT_TERM_MANAGE')")
    public CustomerResponse changePaymentTerm(
            Long customerId,
            ChangeCustomerPaymentTermRequest request
    ) {
        Customer customer = findCustomerById(customerId);
        verifyVersion(customer, request.version());

        PaymentTerm beforePaymentTerm = customer.getPaymentTerm();
        customer.changePaymentTerm(request.paymentTerm());
        customerRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.CUSTOMER_PAYMENT_TERM_CHANGE,
                customerId,
                "修改客戶付款條件",
                "paymentTerm=" + beforePaymentTerm,
                "paymentTerm=" + customer.getPaymentTerm(),
                request.reason().strip()
        );

        return customerMapper.toResponse(customer);
    }

    private Customer findCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    private void verifyVersion(Customer customer, Long expectedVersion) {
        if (!Objects.equals(customer.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void recordStatusChange(
            Customer customer,
            AuditActionType actionType,
            String summary,
            MasterDataStatus beforeStatus,
            String reason
    ) {
        auditService.record(
                currentUserProvider.getCurrentUserId(),
                actionType,
                customer.getId(),
                summary,
                "status=" + beforeStatus,
                "status=" + customer.getStatus(),
                reason.strip()
        );
    }

    private String contactSnapshot(Customer customer) {
        return "contactName=" + customer.getContactName()
                + ", phone=" + customer.getPhone()
                + ", email=" + customer.getEmail()
                + ", address=" + customer.getAddress();
    }
}
