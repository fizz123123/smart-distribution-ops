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
import java.util.Optional;

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

    //某一天有效的版本；版本之間期間不重疊，最多一筆
    @Query("""
            SELECT price
            FROM StandardPrice price
            WHERE price.companyItem.id = :companyItemId
              AND price.pricingUnit = :unit
              AND price.status IN :statuses
              AND price.effectiveFrom <= :date
              AND (price.effectiveTo IS NULL OR price.effectiveTo >= :date)
            """)
    Optional<StandardPrice> findEffectiveOn(
            @Param("companyItemId") Long companyItemId,
            @Param("unit") UnitOfMeasure unit,
            @Param("date") LocalDate date,
            @Param("statuses") Collection<PriceStatus> statuses
    );

    //清單要顯示料號，一起載入避免 N+1
    //效期篩選的規則和 PriceEffectiveState.of 相同；effectiveState 傳列舉名稱，null 代表不篩選
    @EntityGraph(attributePaths = "companyItem")
    @Query("""
            SELECT price
            FROM StandardPrice price
            WHERE (:companyItemId IS NULL OR price.companyItem.id = :companyItemId)
              AND (:status IS NULL OR price.status = :status)
              AND (:effectiveState IS NULL OR (
                  price.status IN :approvedStatuses
                  AND (
                      (:effectiveState = 'UPCOMING' AND price.effectiveFrom > :today)
                      OR (:effectiveState = 'EFFECTIVE' AND price.effectiveFrom <= :today
                          AND (price.effectiveTo IS NULL OR price.effectiveTo >= :today))
                      OR (:effectiveState = 'EXPIRED' AND price.effectiveTo < :today)
                  )
              ))
            """)
    Page<StandardPrice> searchPrices(
            @Param("companyItemId") Long companyItemId,
            @Param("status") PriceStatus status,
            @Param("effectiveState") String effectiveState,
            @Param("approvedStatuses") Collection<PriceStatus> approvedStatuses,
            @Param("today") LocalDate today,
            Pageable pageable
    );
}
