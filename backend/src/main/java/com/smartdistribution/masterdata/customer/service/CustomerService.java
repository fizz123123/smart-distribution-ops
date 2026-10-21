package com.smartdistribution.masterdata.customer.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customer.dto.request.ChangeCustomerPaymentTermRequest;
import com.smartdistribution.masterdata.customer.dto.request.ChangeCustomerStatusRequest;
import com.smartdistribution.masterdata.customer.dto.request.CreateCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.request.SubmitCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.request.UpdateCustomerRequest;
import com.smartdistribution.masterdata.customer.dto.response.CustomerResponse;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CustomerService {

    PageResponse<CustomerResponse> findCustomers(
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    );

    CustomerResponse findCustomer(Long customerId);

    CustomerResponse createCustomer(CreateCustomerRequest request);

    CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request);

    CustomerResponse submitCustomer(Long customerId, SubmitCustomerRequest request);

    CustomerResponse deactivateCustomer(Long customerId, ChangeCustomerStatusRequest request);

    CustomerResponse activateCustomer(Long customerId, ChangeCustomerStatusRequest request);

    CustomerResponse changePaymentTerm(Long customerId, ChangeCustomerPaymentTermRequest request);
}
