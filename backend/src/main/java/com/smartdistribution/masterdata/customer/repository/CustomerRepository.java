package com.smartdistribution.masterdata.customer.repository;

import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByTaxId(String taxId);

    @Query("""
            SELECT customer
            FROM Customer customer
            WHERE (:status IS NULL OR customer.status = :status)
              AND (:keyword IS NULL
                   OR customer.customerCode LIKE CONCAT('%', :keyword, '%')
                   OR customer.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Customer> searchByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") MasterDataStatus status,
            Pageable pageable
    );
}
