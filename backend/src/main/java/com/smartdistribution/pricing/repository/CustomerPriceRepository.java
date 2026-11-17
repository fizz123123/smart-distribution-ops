package com.smartdistribution.pricing.repository;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.pricing.entity.PriceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    boolean existsByCustomerItemMappingIdAndPricingUnitAndStatus(
            Long customerItemMappingId,
            UnitOfMeasure pricingUnit,
            PriceStatus status
    );

    //同一對應＋單位的版本，生效日新的在前；第一筆就是最後一個版本
    @Query("""
            SELECT price
            FROM CustomerPrice price
            WHERE price.customerItemMapping.id = :mappingId
              AND price.pricingUnit = :unit
              AND price.status IN :statuses
            ORDER BY price.effectiveFrom DESC, price.id DESC
            """)
    List<CustomerPrice> findVersionsNewestFirst(
            @Param("mappingId") Long mappingId,
            @Param("unit") UnitOfMeasure unit,
            @Param("statuses") Collection<PriceStatus> statuses
    );

    //某一天有效的版本；版本之間期間不重疊，最多一筆
    @Query("""
            SELECT price
            FROM CustomerPrice price
            WHERE price.customerItemMapping.id = :mappingId
              AND price.pricingUnit = :unit
              AND price.status IN :statuses
              AND price.effectiveFrom <= :date
              AND (price.effectiveTo IS NULL OR price.effectiveTo >= :date)
            """)
    Optional<CustomerPrice> findEffectiveOn(
            @Param("mappingId") Long mappingId,
            @Param("unit") UnitOfMeasure unit,
            @Param("date") LocalDate date,
            @Param("statuses") Collection<PriceStatus> statuses
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
            @Param("status") PriceStatus status,
            Pageable pageable
    );
}
