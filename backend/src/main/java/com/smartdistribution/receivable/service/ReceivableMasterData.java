package com.smartdistribution.receivable.service;

import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 應收模組唯讀查詢主檔的地方：客戶名稱、公司料號品名；應收模組不修改任何主檔
 */
@Component
@RequiredArgsConstructor
public class ReceivableMasterData {

    private final CustomerRepository customerRepository;
    private final CompanyItemRepository companyItemRepository;

    public Map<Long, Customer> findCustomers(Collection<Long> customerIds) {
        return customerRepository.findAllById(customerIds)
                .stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

    public Map<Long, CompanyItem> findCompanyItems(Collection<Long> companyItemIds) {
        return companyItemRepository.findAllById(companyItemIds)
                .stream()
                .collect(Collectors.toMap(CompanyItem::getId, Function.identity()));
    }
}
