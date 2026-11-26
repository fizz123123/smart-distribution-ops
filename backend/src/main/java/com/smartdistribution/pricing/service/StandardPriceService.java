package com.smartdistribution.pricing.service;

import com.smartdistribution.pricing.dto.request.CreateStandardPriceRequest;
import com.smartdistribution.pricing.dto.request.RevokePriceRequest;
import com.smartdistribution.pricing.dto.response.StandardPriceResponse;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface StandardPriceService {

    /**
     * 標準售價歷史，可依料號、審核狀態、效期狀態篩選
     *
     * @param companyItemId null 代表全部
     * @param status 審核狀態，null 代表全部
     * @param effectiveState 依今天判斷的效期狀態，null 代表不篩選；有值時只會查到 ACTIVE、REPLACED
     * @param pageable 分頁與排序
     * @return 一頁價格
     */
    PageResponse<StandardPriceResponse> findPrices(
            Long companyItemId,
            PriceStatus status,
            PriceEffectiveState effectiveState,
            Pageable pageable
    );

    /**
     * 單筆標準售價
     */
    StandardPriceResponse findPrice(Long priceId);

    /**
     * 建立即送覆核
     */
    StandardPriceResponse createPrice(CreateStandardPriceRequest request);

    /**
     * 撤銷價格有誤的版本
     */
    StandardPriceResponse revokePrice(Long priceId, RevokePriceRequest request);
}
