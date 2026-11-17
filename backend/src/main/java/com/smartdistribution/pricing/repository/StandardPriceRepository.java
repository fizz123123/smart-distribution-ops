package com.smartdistribution.pricing.repository;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.entity.PriceStatus;
import com.smartdistribution.pricing.entity.StandardPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StandardPriceRepository extends JpaRepository<StandardPrice, Long> {

    boolean existsByCompanyItemIdAndPricingUnitAndStatus(
            Long companyItemId,
            UnitOfMeasure pricingUnit,
            PriceStatus status
    );

    //同一料號＋單位的版本，生效日新的在前；第一筆就是最後一個版本
    @Query("""
            SELECT price
            FROM StandardPrice price
            WHERE price.companyItem.id = :companyItemId
              AND price.pricingUnit = :unit
              AND price.status IN :statuses
            ORDER BY price.effectiveFrom DESC, price.id DESC
            """)
    List<StandardPrice> findVersionsNewestFirst(
            @Param("companyItemId") Long companyItemId,
            @Param("unit") UnitOfMeasure unit,
            @Param("statuses") Collection<PriceStatus> statuses
    );

    //某一天有效的版本；版本之間期間不重疊，正常只會有一筆
    @Query("""
            SELECT price
            FROM StandardPrice price
            WHERE price.companyItem.id = :companyItemId
              AND price.pricingUnit = :unit
              AND price.status IN :statuses
              AND price.effectiveFrom <= :date
              AND (price.effectiveTo IS NULL OR price.effectiveTo >= :date)
            ORDER BY price.effectiveFrom DESC
            """)
    List<StandardPrice> findEffectiveOn(
            @Param("companyItemId") Long companyItemId,
            @Param("unit") UnitOfMeasure unit,
            @Param("date") LocalDate date,
            @Param("statuses") Collection<PriceStatus> statuses
    );

    //清單要顯示料號，一起載入避免 N+1
    @EntityGraph(attributePaths = "companyItem")
    @Query("""
            SELECT price
            FROM StandardPrice price
            WHERE (:companyItemId IS NULL OR price.companyItem.id = :companyItemId)
              AND (:status IS NULL OR price.status = :status)
            """)
    Page<StandardPrice> searchByItemAndStatus(
            @Param("companyItemId") Long companyItemId,
            @Param("status") PriceStatus status,
            Pageable pageable
    );
}
