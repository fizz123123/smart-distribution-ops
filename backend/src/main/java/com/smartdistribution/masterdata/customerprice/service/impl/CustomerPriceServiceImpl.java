package com.smartdistribution.masterdata.customerprice.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.dto.request.CreateCustomerPriceRequest;
import com.smartdistribution.masterdata.customerprice.dto.request.RevokeCustomerPriceRequest;
import com.smartdistribution.masterdata.customerprice.dto.response.CustomerPriceResponse;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;
import com.smartdistribution.masterdata.customerprice.mapper.CustomerPriceMapper;
import com.smartdistribution.masterdata.customerprice.repository.CustomerPriceRepository;
import com.smartdistribution.masterdata.customerprice.service.CustomerPriceRules;
import com.smartdistribution.masterdata.customerprice.service.CustomerPriceService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerPriceServiceImpl implements CustomerPriceService {

    private final CustomerPriceRepository customerPriceRepository;
    private final CustomerPriceRules customerPriceRules;
    private final ApprovalService approvalService;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerPriceMapper customerPriceMapper;

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER_PRICE_VIEW')")
    public PageResponse<CustomerPriceResponse> findPrices(
            Long customerItemMappingId,
            CustomerPriceStatus status,
            Pageable pageable
    ) {
        Page<CustomerPriceResponse> prices = customerPriceRepository
                .searchByMappingAndStatus(customerItemMappingId, status, pageable)
                .map(customerPriceMapper::toResponse);

        return PageResponse.from(prices);
    }

    @Override
    @PreAuthorize("hasAuthority('CUSTOMER_PRICE_VIEW')")
    public CustomerPriceResponse findPrice(Long priceId) {
        return customerPriceMapper.toResponse(findPriceById(priceId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_PRICE_CREATE')")
    public CustomerPriceResponse createPrice(CreateCustomerPriceRequest request) {
        //先鎖定對應列：同時建立同一對應的價格時，後到者會等前一筆完成才繼續檢查
        CustomerItemMapping mapping =
                customerPriceRules.lockUsableMapping(request.customerItemMappingId());
        customerPriceRules.ensurePricingUnitAllowed(mapping, request.pricingUnit());

        //同一客戶料號與計價單位同時最多一筆待覆核
        if (customerPriceRepository.existsByCustomerItemMappingIdAndPricingUnitAndStatus(
                mapping.getId(),
                request.pricingUnit(),
                CustomerPriceStatus.PENDING_REVIEW
        )) {
            throw new BusinessException(ErrorCode.CUSTOMER_PRICE_PENDING_EXISTS);
        }

        CustomerPrice price = new CustomerPrice(
                mapping,
                request.pricingUnit(),
                request.unitPriceExcludingTax(),
                currentUserProvider.getCurrentUserId()
        );
        customerPriceRepository.saveAndFlush(price);

        //價格沒有草稿，建立後直接送覆核
        approvalService.submit(
                ApprovalTargetType.CUSTOMER_PRICE,
                price.getId(),
                currentUserProvider.getCurrentUserId()
        );

        return customerPriceMapper.toResponse(price);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('CUSTOMER_PRICE_REVOKE')")
    public CustomerPriceResponse revokePrice(Long priceId, RevokeCustomerPriceRequest request) {
        CustomerPrice price = findPriceById(priceId);

        if (!Objects.equals(price.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        //撤銷者、時間與原因存在價格本身，不另寫稽核紀錄
        price.revoke(currentUserProvider.getCurrentUserId(), request.reason(), Instant.now());
        customerPriceRepository.flush();

        return customerPriceMapper.toResponse(price);
    }

    private CustomerPrice findPriceById(Long priceId) {
        return customerPriceRepository.findById(priceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_PRICE_NOT_FOUND));
    }
}
