package com.smartdistribution.pricing.mapper;

import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.pricing.dto.response.StandardPriceResponse;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.pricing.service.PriceEffectiveState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class StandardPriceMapper {

    private final Clock clock;

    public StandardPriceResponse toResponse(StandardPrice price) {
        CompanyItem item = price.getCompanyItem();

        return new StandardPriceResponse(
                price.getId(),
                item.getId(),
                item.getItemCode(),
                item.getName(),
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
