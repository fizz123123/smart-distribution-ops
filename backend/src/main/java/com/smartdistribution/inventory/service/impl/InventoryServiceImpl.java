package com.smartdistribution.inventory.service.impl;

import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.repository.InventoryBalanceRepository;
import com.smartdistribution.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryBalanceRepository inventoryBalanceRepository;

    @Override
    @Transactional
    public void initializeForCompanyItem(Long companyItemId) {
        inventoryBalanceRepository.save(new InventoryBalance(companyItemId));
    }
}
