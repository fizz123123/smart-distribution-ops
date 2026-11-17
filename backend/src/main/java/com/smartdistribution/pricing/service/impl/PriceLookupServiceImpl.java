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
        //版本之間期間不重疊，同一天只會有一個版本有效；被取代的舊版本在截止日之前仍然取得到
        return customerPriceRepository
                .findEffectiveOn(customerItemMappingId, unit, priceDate, CustomerPriceStatus.APPROVED_VERSIONS)
                .stream()
                .findFirst()
                .map(price -> new ApplicablePrice(
                        PriceSource.CUSTOMER_PRICE,
                        price.getId(),
                        price.getUnitPriceExcludingTax()
                ));
    }

    @Override
    public boolean isRevoked(PriceSource source, Long priceId) {
        return switch (source) {
            case CUSTOMER_PRICE -> customerPriceRepository.findById(priceId)
                    .map(price -> price.getStatus() == CustomerPriceStatus.REVOKED)
                    .orElse(false);
        };
    }
}
