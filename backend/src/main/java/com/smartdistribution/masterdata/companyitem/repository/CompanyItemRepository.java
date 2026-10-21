package com.smartdistribution.masterdata.companyitem.repository;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyItemRepository extends JpaRepository<CompanyItem, Long> {

    boolean existsByItemCode(String itemCode);

    @Query("""
            SELECT item
            FROM CompanyItem item
            WHERE (:status IS NULL OR item.status = :status)
              AND (:keyword IS NULL
                   OR item.itemCode LIKE CONCAT('%', :keyword, '%')
                   OR item.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<CompanyItem> searchByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") MasterDataStatus status,
            Pageable pageable
    );
}
