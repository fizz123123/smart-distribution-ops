package com.smartdistribution.identity.config;

import java.util.List;
import java.util.Map;

/**
 * 主管角色包含同部門職員的全部權限，所以只需要指派主管一個角色
 * 指派畫面依這份對照，在勾選主管時把職員顯示為「已包含」並鎖住
 * 實際的權限由 IdentityDataInitializer 指派，兩邊要一致；IdentityApiIntegrationTest 會檢查
 */
public final class RoleInclusions {

    //主管角色 → 它包含的職員角色
    private static final Map<String, List<String>> INCLUDED_ROLES = Map.of(
            "SALES_MANAGER", List.of("SALES"),
            "PURCHASING_MANAGER", List.of("PURCHASING"),
            "WAREHOUSE_MANAGER", List.of("WAREHOUSE")
    );

    private RoleInclusions() {
    }

    public static List<String> includedRoleCodesOf(String roleCode) {
        return INCLUDED_ROLES.getOrDefault(roleCode, List.of());
    }
}
