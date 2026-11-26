package com.smartdistribution.pricing.mapper;

import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.pricing.dto.response.CustomerPriceResponse;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.service.PriceEffectiveState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CustomerPriceMapper {

    private final Clock clock;

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
                price.getStatus(),
                PriceEffectiveState.of(
                        price.getStatus(),
                        price.getEffectiveFrom(),
                        price.getEffectiveTo(),
                        LocalDate.now(clock)
                ),
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
