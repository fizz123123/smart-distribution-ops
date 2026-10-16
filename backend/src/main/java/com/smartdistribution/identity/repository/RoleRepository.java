package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleCode(String roleCode);

    List<Role> findAllByRoleCodeIn(Collection<String> roleCodes);
}
