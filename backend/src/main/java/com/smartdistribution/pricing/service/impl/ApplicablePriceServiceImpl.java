package com.smartdistribution.pricing.service.impl;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.dto.response.ApplicablePriceResponse;
import com.smartdistribution.pricing.dto.response.ApplicablePriceResponse.NextPrice;
import com.smartdistribution.pricing.dto.response.ApplicablePriceResponse.PriceOption;
import com.smartdistribution.pricing.service.ApplicablePriceService;
import com.smartdistribution.pricing.service.PriceLookupService;
import com.smartdistribution.pricing.service.PriceLookupService.ApplicablePrice;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicablePriceServiceImpl implements ApplicablePriceService {

    private final PriceLookupService priceLookupService;
    private final Clock clock;

    @Override
    @PreAuthorize("hasAuthority('SO_CREATE')")
    public ApplicablePriceResponse findApplicablePrice(Long customerItemMappingId, UnitOfMeasure unit, LocalDate date) {
        //和 SO 的訂單日期規則相同：沒帶是今天，不能是未來
        LocalDate today = LocalDate.now(clock);
        LocalDate priceDate = date == null ? today : date;
        if (priceDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "取價日期不能是未來日期。");
        }

        PriceOption current = priceLookupService.findApplicablePrice(customerItemMappingId, unit, priceDate)
                .map(ApplicablePriceServiceImpl::toOption)
                .orElse(null);
        NextPrice next = priceLookupService.findNextChange(customerItemMappingId, unit, priceDate)
                .map(change -> new NextPrice(change.appliesFrom(), toOption(change.price())))
                .orElse(null);

        return new ApplicablePriceResponse(customerItemMappingId, unit, priceDate, current, next);
    }

    private static PriceOption toOption(ApplicablePrice price) {
        if (price == null) {
            return null;
        }

        return new PriceOption(
                price.source(),
                price.priceId(),
                price.unitPriceExcludingTax(),
                price.effectiveFrom(),
                price.effectiveTo(),
                price.source().isUnitPriceEditable()
        );
    }
}
