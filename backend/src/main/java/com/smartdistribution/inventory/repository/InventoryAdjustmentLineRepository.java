package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryAdjustmentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAdjustmentLineRepository extends JpaRepository<InventoryAdjustmentLine, Long> {

    List<InventoryAdjustmentLine> findAllByAdjustmentIdOrderByCompanyItemIdAsc(Long adjustmentId);
}
