package com.smartdistribution.purchasing.service;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.masterdata.supplier.repository.SupplierRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 採購模組唯讀查詢主檔的地方：供應商、公司料號與單位換算；採購模組不修改任何主檔
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderMasterData {

    private final SupplierRepository supplierRepository;
    private final CompanyItemRepository companyItemRepository;
    private final ItemUnitConversionRepository itemUnitConversionRepository;

    public Supplier findSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND));
    }

    //PO 清單一次取回這一頁的供應商
    public Map<Long, Supplier> findSuppliers(Collection<Long> supplierIds) {
        return supplierRepository.findAllById(supplierIds)
                .stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
    }

    public CompanyItem findCompanyItem(Long companyItemId) {
        return companyItemRepository.findById(companyItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND));
    }

    //基準單位倍率固定為 1；其他單位要有換算，找不到代表這個單位不能用
    public Optional<BigDecimal> findConversionFactor(CompanyItem item, UnitOfMeasure unit) {
        if (item.getBaseUnit() == unit) {
            return Optional.of(BigDecimal.ONE);
        }

        return itemUnitConversionRepository.findByCompanyItemIdAndUnit(item.getId(), unit)
                .map(ItemUnitConversion::getFactor);
    }
}
