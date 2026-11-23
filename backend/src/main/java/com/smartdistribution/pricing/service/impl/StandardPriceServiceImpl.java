package com.smartdistribution.pricing.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.pricing.dto.request.CreateStandardPriceRequest;
import com.smartdistribution.pricing.dto.request.RevokePriceRequest;
import com.smartdistribution.pricing.dto.response.StandardPriceResponse;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.pricing.mapper.StandardPriceMapper;
import com.smartdistribution.pricing.repository.StandardPriceRepository;
import com.smartdistribution.pricing.service.PricePeriods;
import com.smartdistribution.pricing.service.StandardPriceRules;
import com.smartdistribution.pricing.service.StandardPriceService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StandardPriceServiceImpl implements StandardPriceService {

    private final StandardPriceRepository standardPriceRepository;
    private final StandardPriceRules standardPriceRules;
    private final ApprovalService approvalService;
    private final CurrentUserProvider currentUserProvider;
    private final StandardPriceMapper standardPriceMapper;
    private final Clock clock;

    @Override
    @PreAuthorize("hasAuthority('STANDARD_PRICE_VIEW')")
    public PageResponse<StandardPriceResponse> findPrices(Long companyItemId, PriceStatus status, Pageable pageable) {
        return PageResponse.from(standardPriceRepository
                .searchByItemAndStatus(companyItemId, status, pageable)
                .map(standardPriceMapper::toResponse));
    }

    @Override
    @PreAuthorize("hasAuthority('STANDARD_PRICE_VIEW')")
    public StandardPriceResponse findPrice(Long priceId) {
        return standardPriceMapper.toResponse(findPriceById(priceId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STANDARD_PRICE_CREATE')")
    public StandardPriceResponse createPrice(CreateStandardPriceRequest request) {
        //先鎖定料號列：同時建立同一料號的標準售價時，後到者會等前一筆完成才繼續檢查
        CompanyItem item = standardPriceRules.lockUsableItem(request.companyItemId());
        standardPriceRules.ensurePricingUnitAllowed(item, request.pricingUnit());
        LocalDate today = LocalDate.now(clock);
        PricePeriods.validateRequested(request.effectiveFrom(), request.effectiveTo(), today);

        //同一料號與計價單位同時最多一筆待覆核
        if (standardPriceRepository.existsByCompanyItemIdAndPricingUnitAndStatus(
                item.getId(),
                request.pricingUnit(),
                PriceStatus.PENDING_REVIEW
        )) {
            throw new BusinessException(ErrorCode.STANDARD_PRICE_PENDING_EXISTS);
        }

        //先擋明顯排不進去的生效日；核准時會依實際生效日再檢查一次
        PricePeriods.ensureNotBefore(
                standardPriceRules.findLatestVersion(item.getId(), request.pricingUnit())
                        .map(StandardPrice::getEffectiveFrom)
                        .orElse(null),
                PricePeriods.resolveEffectiveFrom(request.effectiveFrom(), today)
        );

        StandardPrice price = new StandardPrice(
                item,
                request.pricingUnit(),
                request.unitPriceExcludingTax(),
                request.effectiveFrom(),
                request.effectiveTo(),
                currentUserProvider.getCurrentUserId()
        );
        standardPriceRepository.saveAndFlush(price);

        //價格沒有草稿，建立後直接送覆核
        approvalService.submit(
                ApprovalTargetType.STANDARD_PRICE,
                price.getId(),
                currentUserProvider.getCurrentUserId()
        );

        return standardPriceMapper.toResponse(price);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STANDARD_PRICE_REVOKE')")
    public StandardPriceResponse revokePrice(Long priceId, RevokePriceRequest request) {
        StandardPrice price = findPriceById(priceId);

        if (!Objects.equals(price.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        price.revoke(currentUserProvider.getCurrentUserId(), request.reason(), Instant.now());
        standardPriceRepository.flush();

        return standardPriceMapper.toResponse(price);
    }

    private StandardPrice findPriceById(Long priceId) {
        return standardPriceRepository.findById(priceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STANDARD_PRICE_NOT_FOUND));
    }
}
