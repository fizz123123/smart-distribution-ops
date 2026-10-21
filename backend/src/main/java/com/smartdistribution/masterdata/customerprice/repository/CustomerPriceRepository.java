package com.smartdistribution.masterdata.customerprice.repository;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPriceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    boolean existsByCustomerItemMappingIdAndPricingUnitAndStatus(
            Long customerItemMappingId,
            UnitOfMeasure pricingUnit,
            CustomerPriceStatus status
    );

    Optional<CustomerPrice> findByCustomerItemMappingIdAndPricingUnitAndStatus(
            Long customerItemMappingId,
            UnitOfMeasure pricingUnit,
            CustomerPriceStatus status
    );

    //價格歷史清單要顯示客戶與料號，一起載入避免 N+1
    @EntityGraph(attributePaths = {
            "customerItemMapping",
            "customerItemMapping.customer",
            "customerItemMapping.companyItem"
    })
    @Query("""
            SELECT price
            FROM CustomerPrice price
            WHERE (:mappingId IS NULL OR price.customerItemMapping.id = :mappingId)
              AND (:status IS NULL OR price.status = :status)
            """)
    Page<CustomerPrice> searchByMappingAndStatus(
            @Param("mappingId") Long mappingId,
            @Param("status") CustomerPriceStatus status,
            Pageable pageable
    );
}
