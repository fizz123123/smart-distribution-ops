package com.smartdistribution.pricing.service;

import com.smartdistribution.pricing.dto.request.CreateCustomerPriceRequest;
import com.smartdistribution.pricing.dto.request.RevokePriceRequest;
import com.smartdistribution.pricing.dto.response.CustomerPriceResponse;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CustomerPriceService {

    /**
     * 客戶價格歷史，可依客戶料號、審核狀態、效期狀態篩選
     *
     * @param customerItemMappingId null 代表全部
     * @param status 審核狀態，null 代表全部
     * @param effectiveState 依今天判斷的效期狀態，null 代表不篩選；有值時只會查到 ACTIVE、REPLACED
     * @param pageable 分頁與排序
     * @return 一頁價格
     */
    PageResponse<CustomerPriceResponse> findPrices(
            Long customerItemMappingId,
            PriceStatus status,
            PriceEffectiveState effectiveState,
            Pageable pageable
    );

    CustomerPriceResponse findPrice(Long priceId);

    CustomerPriceResponse createPrice(CreateCustomerPriceRequest request);

    CustomerPriceResponse revokePrice(Long priceId, RevokePriceRequest request);
}
