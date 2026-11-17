package com.smartdistribution.pricing.mapper;

import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.pricing.dto.response.CustomerPriceResponse;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.service.PricePeriods;
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
                price.getEffectiveFrom(),
                price.getEffectiveTo(),
                price.isExpiredOn(PricePeriods.today()),
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
