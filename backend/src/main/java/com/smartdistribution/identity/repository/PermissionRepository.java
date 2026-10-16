package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionCode(String permissionCode);

    List<Permission> findAllByPermissionCodeIn(Collection<String> permissionCodes);
}
