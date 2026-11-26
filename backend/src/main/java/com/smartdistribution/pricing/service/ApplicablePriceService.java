package com.smartdistribution.pricing.service;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.dto.response.ApplicablePriceResponse;

import java.time.LocalDate;

/**
 * 對外的取價查詢，給建立 SO 的畫面預填價格
 * 內部的取價規則在 PriceLookupService；這裡只多了權限、日期檢查與回應格式
 */
public interface ApplicablePriceService {

    /**
     * 查客戶料號在某一天適用的價格，以及之後第一次會變成什麼價格
     *
     * @param customerItemMappingId 客戶料號對應 ID
     * @param unit 計價單位，也就是訂購單位
     * @param date 取價日期，null 代表今天；不能是未來
     * @return 目前適用的價格與下一次變動
     */
    ApplicablePriceResponse findApplicablePrice(Long customerItemMappingId, UnitOfMeasure unit, LocalDate date);
}
