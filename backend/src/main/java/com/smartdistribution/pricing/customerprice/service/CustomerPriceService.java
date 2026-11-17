package com.smartdistribution.pricing.customerprice.service;

import com.smartdistribution.pricing.customerprice.dto.request.CreateCustomerPriceRequest;
import com.smartdistribution.pricing.customerprice.dto.request.RevokeCustomerPriceRequest;
import com.smartdistribution.pricing.customerprice.dto.response.CustomerPriceResponse;
import com.smartdistribution.pricing.customerprice.entity.CustomerPriceStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CustomerPriceService {

    PageResponse<CustomerPriceResponse> findPrices(
            Long customerItemMappingId,
            CustomerPriceStatus status,
            Pageable pageable
    );

    CustomerPriceResponse findPrice(Long priceId);

    CustomerPriceResponse createPrice(CreateCustomerPriceRequest request);

    CustomerPriceResponse revokePrice(Long priceId, RevokeCustomerPriceRequest request);
}
