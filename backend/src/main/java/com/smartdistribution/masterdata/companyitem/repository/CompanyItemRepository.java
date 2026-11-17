package com.smartdistribution.masterdata.companyitem.repository;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyItemRepository extends JpaRepository<CompanyItem, Long> {

    boolean existsByItemCode(String itemCode);

    //標準售價建立與覆核時鎖定料號列，同一料號的標準售價操作依序執行
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT item FROM CompanyItem item WHERE item.id = :id")
    Optional<CompanyItem> findByIdForUpdate(@Param("id") Long id);

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
