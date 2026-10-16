package com.smartdistribution.identity.config;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Permission;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.RolePermission;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.PermissionRepository;
import com.smartdistribution.identity.repository.RolePermissionRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserPermissionGrantRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityDataInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@smartdistribution.local";
    private static final String ADMIN_NAME = "系統管理員";
    private static final String ADMIN_PASSWORD = "Admin1234";

    private static final Map<String, String> ROLES = Map.ofEntries(
            Map.entry("SALES", "業務"),
            Map.entry("SALES_MANAGER", "業務主管"),
            Map.entry("PURCHASING", "採購"),
            Map.entry("PURCHASING_MANAGER", "採購主管"),
            Map.entry("WAREHOUSE", "倉管"),
            Map.entry("WAREHOUSE_MANAGER", "倉管主管"),
            Map.entry("FINANCE", "財務"),
            Map.entry("FINANCE_MANAGER", "財務主管"),
            Map.entry("MANAGER", "營運主管"),
            Map.entry("APP_ADMIN", "系統管理員")
    );

    private static final List<PermissionSeed> PERMISSIONS = List.of(
            new PermissionSeed("USER", "MANAGE"),
            new PermissionSeed("USER_ROLE", "MANAGE"),
            new PermissionSeed("USER_PERMISSION", "MANAGE"),
            new PermissionSeed("SO", "CREATE"),
            new PermissionSeed("CUSTOMER", "APPROVE"),
            new PermissionSeed("SUPPLIER", "APPROVE"),
            new PermissionSeed("COMPANY_ITEM", "APPROVE"),
            new PermissionSeed("CUSTOMER_ITEM_MAPPING", "APPROVE"),
            new PermissionSeed("CUSTOMER_PRICE", "APPROVE")
    );

    private static final List<String> APP_ADMIN_PERMISSIONS = List.of(
            "USER_MANAGE",
            "USER_ROLE_MANAGE",
            "USER_PERMISSION_MANAGE"
    );

    private static final List<String> SALES_MANAGER_APPROVAL_PERMISSIONS = List.of(
            "CUSTOMER_APPROVE",
            "COMPANY_ITEM_APPROVE",
            "CUSTOMER_ITEM_MAPPING_APPROVE",
            "CUSTOMER_PRICE_APPROVE"
    );

    private static final List<String> PURCHASING_MANAGER_APPROVAL_PERMISSIONS = List.of(
            "SUPPLIER_APPROVE"
    );

    private static final List<String> MANAGER_APPROVAL_PERMISSIONS = List.of(
            "CUSTOMER_APPROVE",
            "SUPPLIER_APPROVE"
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionGrantRepository userPermissionGrantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Role> rolesByCode = initializeRoles();
        Map<String, Permission> permissionsByCode = initializePermissions();

        assignPermissions(
                rolesByCode.get("APP_ADMIN"),
                permissionsByCode,
                APP_ADMIN_PERMISSIONS
        );

        assignPermissions(
                rolesByCode.get("SALES_MANAGER"),
                permissionsByCode,
                SALES_MANAGER_APPROVAL_PERMISSIONS
        );

        assignPermissions(
                rolesByCode.get("PURCHASING_MANAGER"),
                permissionsByCode,
                PURCHASING_MANAGER_APPROVAL_PERMISSIONS
        );

        assignPermissions(
                rolesByCode.get("MANAGER"),
                permissionsByCode,
                MANAGER_APPROVAL_PERMISSIONS
        );

        removePermissions(
                rolesByCode.get("MANAGER"),
                permissionsByCode,
                APP_ADMIN_PERMISSIONS
        );

        initializeAdmin(rolesByCode.get("APP_ADMIN"));
    }

    private Map<String, Role> initializeRoles() {
        Map<String, Role> rolesByCode = new LinkedHashMap<>();

        ROLES.forEach((roleCode, name) -> {
            Role role = roleRepository.findByRoleCode(roleCode)
                    .orElseGet(() -> roleRepository.save(new Role(roleCode, name)));

            rolesByCode.put(roleCode, role);
        });

        return rolesByCode;
    }

    private Map<String, Permission> initializePermissions() {
        Map<String, Permission> permissionsByCode = new LinkedHashMap<>();

        for (PermissionSeed seed : PERMISSIONS) {
            String permissionCode = seed.permissionCode();

            Permission permission = permissionRepository
                    .findByPermissionCode(permissionCode)
                    .orElseGet(() -> permissionRepository.save(
                            new Permission(seed.resource(), seed.action())
                    ));

            permissionsByCode.put(permissionCode, permission);
        }

        return permissionsByCode;
    }

    private void assignPermissions(
            Role role,
            Map<String, Permission> permissionsByCode,
            List<String> permissionCodes
    ) {
        for (String permissionCode : permissionCodes) {
            Permission permission = permissionsByCode.get(permissionCode);

            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(
                    role.getId(),
                    permission.getId()
            )) {
                rolePermissionRepository.save(new RolePermission(role, permission));
            }
        }
    }

    private void removePermissions(
            Role role,
            Map<String, Permission> permissionsByCode,
            List<String> permissionCodes
    ) {
        for (String permissionCode : permissionCodes) {
            Permission permission = permissionsByCode.get(permissionCode);

            rolePermissionRepository.deleteByRoleIdAndPermissionId(
                    role.getId(),
                    permission.getId()
            );
        }
    }

    private void initializeAdmin(Role appAdminRole) {
        AppUser admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new AppUser(
                                ADMIN_EMAIL,
                                ADMIN_NAME,
                                passwordEncoder.encode(ADMIN_PASSWORD)
                        )
                ));

        if (!userRoleRepository.existsByUserIdAndRoleId(
                admin.getId(),
                appAdminRole.getId()
        )) {
            userRoleRepository.save(new UserRole(admin, appAdminRole));
        }

        List<UserRole> unauthorizedAppAdminAssignments = userRoleRepository
                .findAllByRoleId(appAdminRole.getId())
                .stream()
                .filter(userRole -> !userRole.getUser().getId().equals(admin.getId()))
                .toList();

        userRoleRepository.deleteAll(unauthorizedAppAdminAssignments);

        List<UserRole> additionalRoles = userRoleRepository.findAllByUserId(admin.getId())
                .stream()
                .filter(userRole -> !userRole.getRole().getId().equals(appAdminRole.getId()))
                .toList();

        userRoleRepository.deleteAll(additionalRoles);
        userPermissionGrantRepository.deleteAll(
                userPermissionGrantRepository.findAllByUserId(admin.getId())
        );

        log.info("Local administrator is ready: {}", ADMIN_EMAIL);
    }

    private record PermissionSeed(String resource, String action) {

        private String permissionCode() {
            return resource + "_" + action;
        }
    }
}
