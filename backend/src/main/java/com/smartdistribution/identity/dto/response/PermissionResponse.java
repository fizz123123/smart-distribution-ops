package com.smartdistribution.identity.dto.response;

public record PermissionResponse(
        String permissionCode,
        String resource,
        String action
) {
}
