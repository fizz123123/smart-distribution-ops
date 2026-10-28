package com.smartdistribution.identity.dto.response;

import java.util.List;

/**
 * @param includedRoleCodes 這個角色包含的其他角色，例如業務主管包含業務；勾選時要把它們顯示為已包含
 */
public record RoleResponse(
        String roleCode,
        String name,
        List<String> includedRoleCodes
) {
}
