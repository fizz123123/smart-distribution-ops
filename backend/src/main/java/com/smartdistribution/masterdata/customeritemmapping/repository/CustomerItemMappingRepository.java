package com.smartdistribution.masterdata.customeritemmapping.repository;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerItemMappingRepository extends JpaRepository<CustomerItemMapping, Long> {

    boolean existsByCustomerIdAndCustomerItemCode(Long customerId, String customerItemCode);

    // 客戶價格建立與覆核時鎖定對應列，同一對應的價格操作依序執行（D-049）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mapping FROM CustomerItemMapping mapping WHERE mapping.id = :id")
    Optional<CustomerItemMapping> findByIdForUpdate(@Param("id") Long id);

    //清單每一筆都要顯示客戶與公司料號，一起載入避免 N+1
    @EntityGraph(attributePaths = {"customer", "companyItem"})
    @Query("""
            SELECT mapping
            FROM CustomerItemMapping mapping
            WHERE (:customerId IS NULL OR mapping.customer.id = :customerId)
              AND (:status IS NULL OR mapping.status = :status)
              AND (:keyword IS NULL
                   OR mapping.customerItemCode LIKE CONCAT('%', :keyword, '%')
                   OR mapping.companyItem.itemCode LIKE CONCAT('%', :keyword, '%')
                   OR mapping.companyItem.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<CustomerItemMapping> searchByCustomerAndKeywordAndStatus(
            @Param("customerId") Long customerId,
            @Param("keyword") String keyword,
            @Param("status") MasterDataStatus status,
            Pageable pageable
    );
}
