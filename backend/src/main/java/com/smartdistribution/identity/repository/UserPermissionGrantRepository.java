package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.UserPermissionGrant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface UserPermissionGrantRepository extends JpaRepository<UserPermissionGrant, Long> {

    @EntityGraph(attributePaths = "permission")
    List<UserPermissionGrant> findAllByUserId(Long userId);

    @Query("""
            SELECT DISTINCT grant.permission.permissionCode
            FROM UserPermissionGrant grant
            WHERE grant.user.id = :userId
              AND grant.validFrom <= :instant
              AND (grant.validTo IS NULL OR grant.validTo > :instant)
            """)
    Set<String> findEffectivePermissionCodesByUserId(
            @Param("userId") Long userId,
            @Param("instant") Instant instant
    );
}
