package com.smartdistribution.pricing.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.pricing.repository.StandardPriceRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 建立與核准標準售價都要檢查的規則，service 與 handler 共用：料號可用、計價單位、版本先後
 * 和 CustomerPriceRules 對稱，鎖定的對象從客戶料號對應換成公司料號
 */
@Component
@RequiredArgsConstructor
public class StandardPriceRules {

    private final CompanyItemRepository companyItemRepository;
    private final ItemUnitConversionRepository itemUnitConversionRepository;
    private final StandardPriceRepository standardPriceRepository;

    /**
     * 鎖定料號列，同一料號的標準售價操作依序執行；料號必須仍為啟用
     */
    public CompanyItem lockUsableItem(Long companyItemId) {
        CompanyItem item = companyItemRepository.findByIdForUpdate(companyItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND));

        if (item.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STANDARD_PRICE_ITEM_INACTIVE);
        }

        return item;
    }

    /**
     * 計價單位須為料號的庫存基準單位，或已設定換算的單位
     */
    public void ensurePricingUnitAllowed(CompanyItem item, UnitOfMeasure pricingUnit) {
        if (pricingUnit == item.getBaseUnit()) {
            return;
        }

        if (!itemUnitConversionRepository.existsByCompanyItemIdAndUnit(item.getId(), pricingUnit)) {
            throw new BusinessException(ErrorCode.STANDARD_PRICE_UNIT_INVALID);
        }
    }

    /**
     * 同一料號＋單位的最後一個版本（生效日最晚的已核准版本）
     */
    public Optional<StandardPrice> findLatestVersion(Long companyItemId, UnitOfMeasure pricingUnit) {
        return standardPriceRepository
                .findVersionsNewestFirst(companyItemId, pricingUnit, PriceStatus.APPROVED_VERSIONS)
                .stream()
                .findFirst();
    }

    /**
     * 新版本的生效日不能早於最後一個版本的生效日
     */
    public void ensureNotBeforeLatest(Optional<StandardPrice> latest, LocalDate effectiveFrom) {
        if (latest.isPresent() && effectiveFrom.isBefore(latest.get().getEffectiveFrom())) {
            throw new BusinessException(
                    ErrorCode.PRICE_PERIOD_CONFLICT,
                    "已有 " + latest.get().getEffectiveFrom() + " 起生效的價格，新價格的生效日不能早於這一天。"
            );
        }
    }
}
