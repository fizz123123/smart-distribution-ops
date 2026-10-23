package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    boolean existsByCompanyItemId(Long companyItemId);

    Page<InventoryTransaction> findAllByCompanyItemId(Long companyItemId, Pageable pageable);

    // 一致性檢查用：每個料號的異動加總
    @Query("""
            SELECT new com.smartdistribution.inventory.repository.QuantityByKey(
                transaction.companyItemId, SUM(transaction.quantityChange))
            FROM InventoryTransaction transaction
            GROUP BY transaction.companyItemId
            """)
    List<QuantityByKey> sumQuantityByCompanyItem();
}
