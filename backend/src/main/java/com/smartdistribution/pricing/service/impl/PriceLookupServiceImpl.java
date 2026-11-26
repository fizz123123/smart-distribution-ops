package com.smartdistribution.pricing.service.impl;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.entity.StandardPrice;
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
import java.util.ArrayList;
import java.util.List;
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

        CustomerItemMapping mapping = findMapping(customerItemMappingId);

        return findStandardPrice(mapping.getCompanyItem().getId(), unit, priceDate);
    }

    @Override
    public Optional<PriceChange> findNextChange(
            Long customerItemMappingId,
            UnitOfMeasure unit,
            LocalDate priceDate
    ) {
        CustomerItemMapping mapping = findMapping(customerItemMappingId);
        ApplicablePrice current = findApplicablePrice(customerItemMappingId, unit, priceDate).orElse(null);

        //價格可能改變的日期：之後才開始的版本（客戶價格或標準售價），以及目前價格到期的隔天
        List<LocalDate> candidates = new ArrayList<>();
        customerPriceRepository
                .findVersionsNewestFirst(customerItemMappingId, unit, PriceStatus.APPROVED_VERSIONS)
                .stream()
                .map(CustomerPrice::getEffectiveFrom)
                .filter(from -> from.isAfter(priceDate))
                .forEach(candidates::add);
        standardPriceRepository
                .findVersionsNewestFirst(mapping.getCompanyItem().getId(), unit, PriceStatus.APPROVED_VERSIONS)
                .stream()
                .map(StandardPrice::getEffectiveFrom)
                .filter(from -> from.isAfter(priceDate))
                .forEach(candidates::add);
        if (current != null && current.effectiveTo() != null) {
            candidates.add(current.effectiveTo().plusDays(1));
        }

        //依日期逐一取價，第一個和目前不同的就是下一次變動；客戶價格蓋過標準售價的情況也一起判斷到
        return candidates.stream()
                .distinct()
                .sorted()
                .map(date -> new PriceChange(date, findApplicablePrice(customerItemMappingId, unit, date).orElse(null)))
                .filter(change -> !isSame(current, change.price()))
                .findFirst();
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

    private CustomerItemMapping findMapping(Long customerItemMappingId) {
        return customerItemMappingRepository.findById(customerItemMappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_NOT_FOUND));
    }

    //兩邊都沒有價格也算相同
    private static boolean isSame(ApplicablePrice current, ApplicablePrice other) {
        return current == null ? other == null : current.isSamePrice(other);
    }

    //版本之間期間不重疊，同一天只會有一個版本有效；被取代的舊版本在截止日之前仍然取得到
    private Optional<ApplicablePrice> findCustomerPrice(Long mappingId, UnitOfMeasure unit, LocalDate date) {
        return customerPriceRepository
                .findEffectiveOn(mappingId, unit, date, PriceStatus.APPROVED_VERSIONS)
                .map(price -> new ApplicablePrice(
                        PriceSource.CUSTOMER_PRICE,
                        price.getId(),
                        price.getUnitPriceExcludingTax(),
                        price.getEffectiveFrom(),
                        price.getEffectiveTo()
                ));
    }

    private Optional<ApplicablePrice> findStandardPrice(Long companyItemId, UnitOfMeasure unit, LocalDate date) {
        return standardPriceRepository
                .findEffectiveOn(companyItemId, unit, date, PriceStatus.APPROVED_VERSIONS)
                .map(price -> new ApplicablePrice(
                        PriceSource.STANDARD_PRICE,
                        price.getId(),
                        price.getUnitPriceExcludingTax(),
                        price.getEffectiveFrom(),
                        price.getEffectiveTo()
                ));
    }
}
