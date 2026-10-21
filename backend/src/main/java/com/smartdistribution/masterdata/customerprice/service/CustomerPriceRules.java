package com.smartdistribution.masterdata.customerprice.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 建立價格與核准價格都要檢查的規則，service 與 handler 共用
 * handler 不能依賴 CustomerPriceService（會形成循環依賴），所以抽成獨立元件
 */
@Component
@RequiredArgsConstructor
public class CustomerPriceRules {

    private final CustomerItemMappingRepository customerItemMappingRepository;
    private final ItemUnitConversionRepository itemUnitConversionRepository;

    /**
     * 鎖定對應列，同一對應的價格操作依序執行；對應、客戶與公司料號都必須仍為啟用
     */
    public CustomerItemMapping lockUsableMapping(Long mappingId) {
        CustomerItemMapping mapping = customerItemMappingRepository.findByIdForUpdate(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_NOT_FOUND));

        if (mapping.getStatus() != MasterDataStatus.ACTIVE || !mapping.hasActiveReferences()) {
            throw new BusinessException(ErrorCode.CUSTOMER_PRICE_MAPPING_INACTIVE);
        }

        return mapping;
    }

    /**
     * 計價單位須為料號的庫存基準單位，或已設定換算的單位
     */
    public void ensurePricingUnitAllowed(CustomerItemMapping mapping, UnitOfMeasure pricingUnit) {
        CompanyItem companyItem = mapping.getCompanyItem();

        if (pricingUnit == companyItem.getBaseUnit()) {
            return;
        }

        if (!itemUnitConversionRepository.existsByCompanyItemIdAndUnit(companyItem.getId(), pricingUnit)) {
            throw new BusinessException(ErrorCode.CUSTOMER_PRICE_UNIT_INVALID);
        }
    }
}
