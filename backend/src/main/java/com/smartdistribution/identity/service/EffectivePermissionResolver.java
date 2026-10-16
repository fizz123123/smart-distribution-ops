package com.smartdistribution.identity.service;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.repository.RolePermissionRepository;
import com.smartdistribution.identity.repository.UserPermissionGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class EffectivePermissionResolver {

    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionGrantRepository userPermissionGrantRepository;

    public Set<String> resolve(AppUser user, Instant evaluatedAt) {
        if (!user.isActive()) {
            return Set.of();
        }

        Set<String> permissionCodes = new HashSet<>(
                rolePermissionRepository.findPermissionCodesByUserId(user.getId())
        );

        permissionCodes.addAll(
                userPermissionGrantRepository.findEffectivePermissionCodesByUserId(
                        user.getId(),
                        evaluatedAt
                )
        );

        return permissionCodes;
    }
}
