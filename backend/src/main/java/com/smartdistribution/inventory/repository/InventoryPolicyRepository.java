package com.smartdistribution.inventory.repository;

import com.smartdistribution.inventory.entity.InventoryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryPolicyRepository extends JpaRepository<InventoryPolicy, Long> {

    Optional<InventoryPolicy> findByCompanyItemId(Long companyItemId);
}
