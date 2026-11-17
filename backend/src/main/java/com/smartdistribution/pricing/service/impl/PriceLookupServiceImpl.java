package com.smartdistribution.pricing.service.impl;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.entity.CustomerPriceStatus;
import com.smartdistribution.pricing.repository.CustomerPriceRepository;
import com.smartdistribution.pricing.service.PriceLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceLookupServiceImpl implements PriceLookupService {

    private final CustomerPriceRepository customerPriceRepository;

    @Override
    public Optional<ApplicablePrice> findApplicablePrice(
            Long customerItemMappingId,
            UnitOfMeasure unit,
            LocalDate priceDate
    ) {
        //同一對應＋單位同時只會有一筆 ACTIVE，核准新價格時舊的會轉 REPLACED
        return customerPriceRepository
                .findByCustomerItemMappingIdAndPricingUnitAndStatus(
                        customerItemMappingId,
                        unit,
                        CustomerPriceStatus.ACTIVE
                )
                .map(price -> new ApplicablePrice(
                        PriceSource.CUSTOMER_PRICE,
                        price.getId(),
                        price.getUnitPriceExcludingTax()
                ));
    }

    @Override
    public boolean isRevoked(PriceSource source, Long priceId) {
        return hasStatus(source, priceId, CustomerPriceStatus.REVOKED);
    }

    @Override
    public boolean isReplaced(PriceSource source, Long priceId) {
        return hasStatus(source, priceId, CustomerPriceStatus.REPLACED);
    }

    private boolean hasStatus(PriceSource source, Long priceId, CustomerPriceStatus status) {
        return switch (source) {
            case CUSTOMER_PRICE -> customerPriceRepository.findById(priceId)
                    .map(price -> price.getStatus() == status)
                    .orElse(false);
        };
    }
}
