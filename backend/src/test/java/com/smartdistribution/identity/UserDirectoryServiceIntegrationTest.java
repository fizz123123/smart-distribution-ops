package com.smartdistribution.identity;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Permission;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserPermissionGrant;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.PermissionRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserPermissionGrantRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.identity.service.UserDirectoryService.UserContact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class UserDirectoryServiceIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@directory.integration.test";
    private static final String PERMISSION = "SUPPLIER_APPROVE";

    @Autowired
    private UserDirectoryService userDirectoryService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserPermissionGrantRepository userPermissionGrantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM app_user WHERE email LIKE ?",
                Long.class,
                "%" + TEST_EMAIL_SUFFIX
        );

        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM user_permission_grant WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void activeUsersWithPermissionIncludeRolesAndEffectiveGrants() {
        Instant now = Instant.now();
        AppUser byRole = createUser("by-role", "PURCHASING_MANAGER");
        AppUser byGrant = createUser("by-grant", "WAREHOUSE");
        grant(byGrant, now.minus(Duration.ofDays(1)), null);
        AppUser expiredGrant = createUser("expired-grant", "WAREHOUSE");
        grant(expiredGrant, now.minus(Duration.ofDays(10)), now.minus(Duration.ofDays(1)));
        AppUser disabled = createUser("disabled", "PURCHASING_MANAGER");
        disabled.disable();
        userRepository.saveAndFlush(disabled);
        AppUser withoutPermission = createUser("without", "SALES");

        List<Long> ids = userDirectoryService.findActiveUsersWithPermission(PERMISSION)
                .stream()
                .map(UserContact::id)
                .toList();

        assertThat(ids).contains(byRole.getId(), byGrant.getId());
        assertThat(ids).doesNotContain(expiredGrant.getId(), disabled.getId(), withoutPermission.getId());
    }

    @Test
    void contactsByIdsSkipInactiveUsers() {
        AppUser active = createUser("contact-active", "SALES");
        AppUser disabled = createUser("contact-disabled", "SALES");
        disabled.disable();
        userRepository.saveAndFlush(disabled);

        List<UserContact> contacts = userDirectoryService.findActiveContactsByIds(
                Set.of(active.getId(), disabled.getId())
        );

        assertThat(contacts)
                .extracting(UserContact::id, UserContact::email)
                .containsExactly(tuple(active.getId(), active.getEmail()));
    }

    private AppUser createUser(String prefix, String roleCode) {
        AppUser user = userRepository.saveAndFlush(new AppUser(
                prefix + "-" + UUID.randomUUID() + TEST_EMAIL_SUFFIX,
                prefix,
                "not-used"
        ));
        Role role = roleRepository.findByRoleCode(roleCode).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(user, role));

        return user;
    }

    private void grant(AppUser user, Instant validFrom, Instant validTo) {
        Permission permission = permissionRepository.findByPermissionCode(PERMISSION).orElseThrow();
        userPermissionGrantRepository.saveAndFlush(
                new UserPermissionGrant(user, permission, "整合測試個別授權", validFrom, validTo)
        );
    }
}
