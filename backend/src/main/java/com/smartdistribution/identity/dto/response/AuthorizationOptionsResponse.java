package com.smartdistribution.identity.dto.response;

import java.util.List;

public record AuthorizationOptionsResponse(
        List<RoleResponse> roles,
        List<PermissionResponse> permissions
) {
}
