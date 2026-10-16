package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUserIdIn(Collection<Long> userIds);

    @EntityGraph(attributePaths = "user")
    List<UserRole> findAllByRoleId(Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    boolean existsByUserIdAndRoleRoleCode(Long userId, String roleCode);
}
