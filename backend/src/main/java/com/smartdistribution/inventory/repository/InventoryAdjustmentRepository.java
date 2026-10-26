package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {
}
