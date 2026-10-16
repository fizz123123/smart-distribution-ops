package com.smartdistribution.identity.dto.response;

import com.smartdistribution.identity.entity.AppUserStatus;

import java.time.Instant;
import java.util.List;

public record UserDetailResponse(
        Long id,
        String email,
        String name,
        AppUserStatus status,
        List<RoleResponse> roles,
        List<PermissionResponse> effectivePermissions,
        List<PermissionGrantResponse> permissionGrants,
        Instant authorizationUpdatedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
