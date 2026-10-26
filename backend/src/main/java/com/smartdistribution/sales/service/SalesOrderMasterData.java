package com.smartdistribution.sales.service;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;
import com.smartdistribution.masterdata.customerprice.repository.CustomerPriceRepository;
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
 * 銷售模組唯讀取用主檔的地方：客戶、客戶料號對應、單位換算與 ACTIVE 價格
 * SO 服務與核准 handler 共用，主檔的查法只寫一次；銷售模組不修改任何主檔
 */
@Component
@RequiredArgsConstructor
public class SalesOrderMasterData {

    private final CustomerRepository customerRepository;
    private final CompanyItemRepository companyItemRepository;
    private final CustomerItemMappingRepository customerItemMappingRepository;
    private final ItemUnitConversionRepository itemUnitConversionRepository;
    private final CustomerPriceRepository customerPriceRepository;

    public Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    //清單一次取回多個客戶，避免每一筆 SO 各查一次
    public Map<Long, Customer> findCustomers(Collection<Long> customerIds) {
        return customerRepository.findAllById(customerIds)
                .stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

    //送審後的明細只存公司料號 ID；料號與品名啟用後鎖定，直接讀目前的值顯示
    public Map<Long, CompanyItem> findCompanyItems(Collection<Long> companyItemIds) {
        return companyItemRepository.findAllById(companyItemIds)
                .stream()
                .collect(Collectors.toMap(CompanyItem::getId, Function.identity()));
    }

    public CustomerItemMapping findMapping(Long mappingId) {
        return customerItemMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_ITEM_MAPPING_NOT_FOUND));
    }

    //對應本身、客戶與公司料號都必須是 ACTIVE，才能用在正式交易
    public boolean isUsable(CustomerItemMapping mapping) {
        return mapping.getStatus() == MasterDataStatus.ACTIVE && mapping.hasActiveReferences();
    }

    //基準單位倍率固定為 1；其他單位要有換算，找不到代表這個單位不能用
    public Optional<BigDecimal> findConversionFactor(CompanyItem item, UnitOfMeasure unit) {
        if (item.getBaseUnit() == unit) {
            return Optional.of(BigDecimal.ONE);
        }

        return itemUnitConversionRepository.findByCompanyItemIdAndUnit(item.getId(), unit)
                .map(ItemUnitConversion::getFactor);
    }

    //只能用有 ACTIVE 價格的單位下單，計價單位就是訂購單位
    public Optional<CustomerPrice> findActivePrice(Long mappingId, UnitOfMeasure unit) {
        return customerPriceRepository.findByCustomerItemMappingIdAndPricingUnitAndStatus(
                mappingId,
                unit,
                CustomerPriceStatus.ACTIVE
        );
    }

    public Optional<CustomerPrice> findPrice(Long priceId) {
        return customerPriceRepository.findById(priceId);
    }
}
