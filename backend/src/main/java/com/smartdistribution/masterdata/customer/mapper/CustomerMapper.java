package com.smartdistribution.masterdata.customer.mapper;

import com.smartdistribution.masterdata.customer.dto.response.CustomerResponse;
import com.smartdistribution.masterdata.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getName(),
                customer.getTaxId(),
                customer.getContactName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getPaymentTerm(),
                customer.getStatus(),
                customer.getCreatedBy(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getVersion()
        );
    }
}
