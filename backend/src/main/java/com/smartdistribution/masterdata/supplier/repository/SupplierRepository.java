package com.smartdistribution.masterdata.supplier.repository;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByTaxId(String taxId);

    @Query("""
            SELECT supplier
            FROM Supplier supplier
            WHERE (:status IS NULL OR supplier.status = :status)
              AND (:keyword IS NULL
                   OR supplier.supplierCode LIKE CONCAT('%', :keyword, '%')
                   OR supplier.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Supplier> searchByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") MasterDataStatus status,
            Pageable pageable
    );
}
