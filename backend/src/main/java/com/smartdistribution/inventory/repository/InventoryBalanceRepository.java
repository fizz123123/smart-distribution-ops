package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
}
