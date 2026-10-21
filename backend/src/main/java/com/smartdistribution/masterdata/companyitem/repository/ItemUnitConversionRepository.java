package com.smartdistribution.masterdata.companyitem.repository;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemUnitConversionRepository extends JpaRepository<ItemUnitConversion, Long> {

    List<ItemUnitConversion> findAllByCompanyItemIdOrderByUnitAsc(Long companyItemId);

    boolean existsByCompanyItemId(Long companyItemId);

    boolean existsByCompanyItemIdAndUnit(Long companyItemId, UnitOfMeasure unit);
}
