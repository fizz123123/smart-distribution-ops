package com.smartdistribution.pricing.service;

import com.smartdistribution.pricing.dto.request.CreateCustomerPriceRequest;
import com.smartdistribution.pricing.dto.request.RevokeCustomerPriceRequest;
import com.smartdistribution.pricing.dto.response.CustomerPriceResponse;
import com.smartdistribution.pricing.entity.CustomerPriceStatus;
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
