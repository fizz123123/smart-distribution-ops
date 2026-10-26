package com.smartdistribution.masterdata.customeritemmapping.mapper;

import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customeritemmapping.dto.response.CustomerItemMappingResponse;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.springframework.stereotype.Component;

@Component
public class CustomerItemMappingMapper {

    public CustomerItemMappingResponse toResponse(CustomerItemMapping mapping) {
        Customer customer = mapping.getCustomer();
        CompanyItem companyItem = mapping.getCompanyItem();

        return new CustomerItemMappingResponse(
                mapping.getId(),
                new CustomerItemMappingResponse.CustomerRef(
                        customer.getId(),
                        customer.getCustomerCode(),
                        customer.getName()
                ),
                mapping.getCustomerItemCode(),
                new CustomerItemMappingResponse.CompanyItemRef(
                        companyItem.getId(),
                        companyItem.getItemCode(),
                        companyItem.getName(),
                        companyItem.getBaseUnit()
                ),
                mapping.getStatus(),
                mapping.getCreatedBy(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt(),
                mapping.getVersion()
        );
    }
}
