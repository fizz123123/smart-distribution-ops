package com.smartdistribution.masterdata.customerprice.mapper;

import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.dto.response.CustomerPriceResponse;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import org.springframework.stereotype.Component;

@Component
public class CustomerPriceMapper {

    public CustomerPriceResponse toResponse(CustomerPrice price) {
        CustomerItemMapping mapping = price.getCustomerItemMapping();

        return new CustomerPriceResponse(
                price.getId(),
                mapping.getId(),
                mapping.getCustomer().getCustomerCode(),
                mapping.getCustomerItemCode(),
                mapping.getCompanyItem().getItemCode(),
                price.getPricingUnit(),
                price.getUnitPriceExcludingTax(),
                price.getStatus(),
                price.getCreatedBy(),
                price.getCreatedAt(),
                price.getReviewedBy(),
                price.getReviewedAt(),
                price.getRevokedBy(),
                price.getRevokedAt(),
                price.getRevokeReason(),
                price.getVersion()
        );
    }
}
