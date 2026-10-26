package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    Optional<InventoryBalance> findByCompanyItemId(Long companyItemId);

    List<InventoryBalance> findAllByCompanyItemIdIn(List<Long> companyItemIds);

    //SELECT … FOR UPDATE：同一料號的庫存與預留操作會在這裡依序進行
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT balance FROM InventoryBalance balance WHERE balance.companyItemId = :companyItemId")
    Optional<InventoryBalance> findByCompanyItemIdForUpdate(@Param("companyItemId") Long companyItemId);

    @Query("""
            SELECT new com.smartdistribution.inventory.repository.InventoryBalanceProjection(
                item.id,
                item.itemCode,
                item.name,
                item.baseUnit,
                balance.onHandQty,
                policy.safetyStockBaseQty
            )
            FROM InventoryBalance balance
            JOIN CompanyItem item ON item.id = balance.companyItemId
            JOIN InventoryPolicy policy ON policy.companyItem.id = balance.companyItemId
            WHERE :keyword IS NULL
               OR item.itemCode LIKE CONCAT('%', :keyword, '%')
               OR item.name LIKE CONCAT('%', :keyword, '%')
            ORDER BY item.itemCode
            """)
    Page<InventoryBalanceProjection> searchBalances(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
