package com.smartdistribution.identity.mapper;

import com.smartdistribution.identity.dto.response.PermissionResponse;
import com.smartdistribution.identity.dto.response.RoleResponse;
import com.smartdistribution.identity.dto.response.UserDetailResponse;
import com.smartdistribution.identity.dto.response.UserSummaryResponse;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Permission;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserPermissionGrant;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class UserMapper {

    public UserSummaryResponse toSummary(
            AppUser user,
            Collection<Role> roles
    ) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getStatus(),
                toRoleResponses(roles),
                user.getVersion()
        );
    }

    public UserDetailResponse toDetail(
            AppUser user,
            Collection<Role> roles,
            Collection<Permission> effectivePermissions,
            Collection<UserPermissionGrant> permissionGrants,
            Instant evaluatedAt
    ) {
        var permissionResponses = effectivePermissions.stream()
                .map(this::toPermissionResponse)
                .sorted(Comparator.comparing(PermissionResponse::permissionCode))
                .toList();

        var permissionGrantResponses = permissionGrants.stream()
                .map(grant -> toPermissionGrant(grant, evaluatedAt))
                .sorted(Comparator.comparing(response ->
                        response.permission().permissionCode()
                )).toList();

        return new UserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getStatus(),
                toRoleResponses(roles),
                permissionResponses,
                permissionGrantResponses,
                user.getAuthorizationUpdatedAt(),
                user.getVersion(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private List<RoleResponse> toRoleResponses(Collection<Role> roles) {
        return roles.stream()
                .map(this::toRoleResponse)
                .sorted(Comparator.comparing(RoleResponse::roleCode))
                .toList();
    }

    public RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(
                role.getRoleCode(),
                role.getName()
        );
    }

    public PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission.getPermissionCode(),
                permission.getResource(),
                permission.getAction()
        );
    }

    private UserDetailResponse.PermissionGrant toPermissionGrant(
            UserPermissionGrant grant,
            Instant evaluatedAt
    ) {
        return new UserDetailResponse.PermissionGrant(
                toPermissionResponse(grant.getPermission()),
                grant.getReason(),
                grant.getValidFrom(),
                grant.getValidTo(),
                grant.isEffectiveAt(evaluatedAt)
        );
    }
}
