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
        List<PermissionGrant> permissionGrants,
        Instant authorizationUpdatedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    // effective：依目前時間判斷這筆個別授權是否在有效期間內
    public record PermissionGrant(
            PermissionResponse permission,
            String reason,
            Instant validFrom,
            Instant validTo,
            boolean effective
    ) {
    }
}
