package com.smartdistribution.masterdata.companyitem.repository;

import com.smartdistribution.masterdata.companyitem.entity.InventoryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryPolicyRepository extends JpaRepository<InventoryPolicy, Long> {

    Optional<InventoryPolicy> findByCompanyItemId(Long companyItemId);
}
