package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryAdjustmentLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InventoryAdjustmentLineRepository extends JpaRepository<InventoryAdjustmentLine, Long> {

    List<InventoryAdjustmentLine> findAllByAdjustmentIdOrderByCompanyItemIdAsc(Long adjustmentId);

    //哪些原明細已經被沖銷：回傳沖銷它們的更正單明細
    @EntityGraph(attributePaths = "adjustment")
    List<InventoryAdjustmentLine> findAllByReversalOfLineIdIn(Collection<Long> originalLineIds);
}
