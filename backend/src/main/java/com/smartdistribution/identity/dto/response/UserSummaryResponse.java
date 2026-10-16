package com.smartdistribution.identity.dto.response;

import com.smartdistribution.identity.entity.AppUserStatus;

import java.util.List;

public record UserSummaryResponse(
        Long id,
        String email,
        String name,
        AppUserStatus status,
        List<RoleResponse> roles,
        Long version
) {
}
