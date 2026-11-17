package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryAdjustment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    //SELECT … FOR UPDATE：同一張調整的更正依序進行，後到的才看得到前一次沖銷了哪些明細
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT adjustment FROM InventoryAdjustment adjustment WHERE adjustment.id = :id")
    Optional<InventoryAdjustment> findByIdForUpdate(@Param("id") Long id);
}
