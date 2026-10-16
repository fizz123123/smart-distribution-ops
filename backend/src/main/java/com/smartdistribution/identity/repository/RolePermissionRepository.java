package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    long deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @Query("""
            SELECT DISTINCT rolePermission.permission.permissionCode
            FROM RolePermission rolePermission
            WHERE EXISTS (
                  SELECT userRole.id
                  FROM UserRole userRole
                  WHERE userRole.user.id = :userId
                    AND userRole.role = rolePermission.role
              )
            """)
    Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);

}
