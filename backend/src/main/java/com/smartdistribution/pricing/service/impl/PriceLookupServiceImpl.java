package com.smartdistribution.pricing.service.impl;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.repository.CustomerPriceRepository;
import com.smartdistribution.pricing.repository.StandardPriceRepository;
import com.smartdistribution.pricing.service.PriceLookupService;
import com.smartdistribution.pricing.service.PriceSource;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
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
    private final StandardPriceRepository standardPriceRepository;
    private final CustomerItemMappingRepository customerItemMappingRepository;

    @Override
    public Optional<ApplicablePrice> findApplicablePrice(
            Long customerItemMappingId,
            UnitOfMeasure unit,
            LocalDate priceDate
    ) {
        Optional<ApplicablePrice> customerPrice = findCustomerPrice(customerItemMappingId, unit, priceDate);
        if (customerPrice.isPresent()) {
            return customerPrice;
        }

        CustomerItemMapping mapping = customerItemMappingRepository.findById(customerItemMappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_NOT_FOUND));

        return findStandardPrice(mapping.getCompanyItem().getId(), unit, priceDate);
    }

    @Override
    public boolean isRevoked(PriceSource source, Long priceId) {
        return switch (source) {
            case CUSTOMER_PRICE -> customerPriceRepository.findById(priceId)
                    .map(price -> price.getStatus() == PriceStatus.REVOKED)
                    .orElse(false);
            case STANDARD_PRICE -> standardPriceRepository.findById(priceId)
                    .map(price -> price.getStatus() == PriceStatus.REVOKED)
                    .orElse(false);
        };
    }

    //版本之間期間不重疊，同一天只會有一個版本有效；被取代的舊版本在截止日之前仍然取得到
    private Optional<ApplicablePrice> findCustomerPrice(Long mappingId, UnitOfMeasure unit, LocalDate date) {
        return customerPriceRepository
                .findEffectiveOn(mappingId, unit, date, PriceStatus.APPROVED_VERSIONS)
                .map(price -> new ApplicablePrice(
                        PriceSource.CUSTOMER_PRICE,
                        price.getId(),
                        price.getUnitPriceExcludingTax()
                ));
    }

    private Optional<ApplicablePrice> findStandardPrice(Long companyItemId, UnitOfMeasure unit, LocalDate date) {
        return standardPriceRepository
                .findEffectiveOn(companyItemId, unit, date, PriceStatus.APPROVED_VERSIONS)
                .map(price -> new ApplicablePrice(
                        PriceSource.STANDARD_PRICE,
                        price.getId(),
                        price.getUnitPriceExcludingTax()
                ));
    }
}
