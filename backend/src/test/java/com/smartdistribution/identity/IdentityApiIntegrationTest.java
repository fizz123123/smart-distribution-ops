package com.smartdistribution.identity;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.config.RoleInclusions;
import com.smartdistribution.shared.security.config.SecurityProperties;
import com.smartdistribution.support.RecordingMailSender;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityApiIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@smartdistribution.local";
    private static final String ADMIN_PASSWORD = "Admin1234";
    private static final String TEST_EMAIL_SUFFIX = "@integration.test";
    private static final String TEST_REASON_PREFIX = "IT-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private RecordingMailSender mailSender;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> testUserIds = jdbcTemplate.queryForList(
                "SELECT id FROM app_user WHERE email LIKE ?",
                Long.class,
                "%" + TEST_EMAIL_SUFFIX
        );

        for (Long userId : testUserIds) {
            jdbcTemplate.update(
                    "DELETE FROM audit_log "
                            + "WHERE actor_id = ? OR (target_type = 'USER' AND target_id = ?)",
                    userId,
                    userId
            );
            jdbcTemplate.update("DELETE FROM password_token WHERE user_id = ?", userId);
            jdbcTemplate.update(
                    "DELETE FROM user_permission_grant WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);
        }

        jdbcTemplate.update(
                "DELETE FROM audit_log WHERE reason LIKE ?",
                TEST_REASON_PREFIX + "%"
        );
    }

    @Test
    void loginThenReadCurrentUser() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0]").value("APP_ADMIN"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions.length()").value(3));
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void expiredTokenIsReportedForProtectedEndpointButDoesNotBlockLogin() throws Exception {
        String expiredToken = expiredToken();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", bearer(expiredToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void appAdminCanCreateUserAssignAuthorizationAndDisableAccount() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("sales");

        UserVersion user = createUser(accessToken, email, "整合測試業務");
        user = replaceRoles(
                accessToken,
                user,
                "SALES",
                TEST_REASON_PREFIX + "assign-sales-role"
        );
        user = replacePermissionGrants(accessToken, user);

        MvcResult disableResult = mockMvc.perform(post("/api/v1/users/{userId}/disable", user.id())
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "IT-disable-test-user",
                                  "version": %d
                                }
                                """.formatted(user.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.roles[0].roleCode").value("SALES"))
                .andExpect(jsonPath("$.effectivePermissions").isEmpty())
                .andReturn();

        Long targetUserId = readLong(disableResult, "$.id");
        Integer successAuditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_log
                WHERE target_type = 'USER'
                  AND target_id = ?
                """,
                Integer.class,
                targetUserId
        );

        assertThat(successAuditCount).isEqualTo(4);
    }

    @Test
    void userCannotReplaceOwnRoles() throws Exception {
        MvcResult loginResult = performLogin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String accessToken = JsonPath.read(responseBody(loginResult), "$.accessToken");
        Long adminId = readLong(loginResult, "$.user.id");
        Long adminVersion = findUserVersion(accessToken, adminId);
        String reason = TEST_REASON_PREFIX + "self-role-change";

        mockMvc.perform(put("/api/v1/users/{userId}/roles", adminId)
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCodes": ["APP_ADMIN"],
                                  "reason": "%s",
                                  "version": %d
                                }
                                """.formatted(reason, adminVersion)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("USER_SELF_MANAGEMENT_FORBIDDEN"));
    }

    @Test
    void userManagementPermissionsCannotBeIndividuallyGranted() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UserVersion user = createUser(
                accessToken,
                testEmail("non-admin-grant"),
                "整合測試一般使用者"
        );

        mockMvc.perform(put("/api/v1/users/{userId}/permission-grants", user.id())
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grants": [
                                    {
                                      "permissionCode": "USER_ROLE_MANAGE",
                                      "reason": "IT-forbidden-admin-grant",
                                      "validFrom": "2026-01-01T00:00:00Z",
                                      "validTo": null
                                    }
                                  ],
                                  "reason": "IT-forbidden-admin-grant",
                                  "version": %d
                                }
                                """.formatted(user.version())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("USER_MANAGEMENT_PERMISSION_NOT_GRANTABLE"));

        Integer grantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_permission_grant WHERE user_id = ?",
                Integer.class,
                user.id()
        );
        assertThat(grantCount).isZero();
    }

    @Test
    void permissionGrantsCanGrowBeyondThreeButDuplicatesAreRejected() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UserVersion user = createUser(accessToken, testEmail("many-grants"), "整合測試多筆授權");

        MvcResult threeGrants = mockMvc.perform(put("/api/v1/users/{userId}/permission-grants", user.id())
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantsRequest(List.of("SUPPLIER_VIEW", "SO_VIEW", "CUSTOMER_VIEW"), user.version())))
                .andExpect(status().isOk())
                .andReturn();
        user = readUserVersion(threeGrants);

        //已有 3 筆，再加第 4 筆不同的權限要能成功；稽核紀錄的內容會超過 255 個字
        MvcResult fourGrants = mockMvc.perform(put("/api/v1/users/{userId}/permission-grants", user.id())
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantsRequest(
                                List.of("SUPPLIER_VIEW", "SO_VIEW", "CUSTOMER_VIEW", "INVENTORY_VIEW"),
                                user.version()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionGrants.length()").value(4))
                .andReturn();
        user = readUserVersion(fourGrants);

        Integer longestAfterData = jdbcTemplate.queryForObject(
                "SELECT MAX(CHAR_LENGTH(after_data)) FROM audit_log WHERE target_type = 'USER' AND target_id = ?",
                Integer.class,
                user.id()
        );
        assertThat(longestAfterData).isGreaterThan(255);

        //同一個權限送兩次，回 400 並說明原因，原本的 4 筆不受影響
        mockMvc.perform(put("/api/v1/users/{userId}/permission-grants", user.id())
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantsRequest(
                                List.of("SUPPLIER_VIEW", "SO_VIEW", "CUSTOMER_VIEW", "INVENTORY_VIEW", "SUPPLIER_VIEW"),
                                user.version()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_GRANT_DUPLICATED"));

        Integer grantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_permission_grant WHERE user_id = ?",
                Integer.class,
                user.id()
        );
        assertThat(grantCount).isEqualTo(4);
    }

    @Test
    void appAdminCanReadAssignableAuthorizationOptions() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/v1/users/authorization-options")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> roleCodes = JsonPath.read(
                responseBody(result),
                "$.roles[*].roleCode"
        );
        List<String> permissionCodes = JsonPath.read(
                responseBody(result),
                "$.permissions[*].permissionCode"
        );

        assertThat(roleCodes)
                .contains("SALES", "MANAGER")
                .doesNotContain("APP_ADMIN");

        //主管角色標出它包含的職員角色，前端勾選主管時把職員顯示為已包含
        List<String> salesManagerIncludes = JsonPath.read(
                responseBody(result),
                "$.roles[?(@.roleCode == 'SALES_MANAGER')].includedRoleCodes[*]"
        );
        List<String> managerIncludes = JsonPath.read(
                responseBody(result),
                "$.roles[?(@.roleCode == 'MANAGER')].includedRoleCodes[*]"
        );
        assertThat(salesManagerIncludes).containsExactly("SALES");
        assertThat(managerIncludes).isEmpty();
        assertThat(permissionCodes)
                .contains("SO_CREATE")
                .doesNotContain(
                        "USER_MANAGE",
                        "USER_ROLE_MANAGE",
                        "USER_PERMISSION_MANAGE"
                );
    }

    @Test
    void appAdminRoleIsReservedAndManagerHasNoUserManagementPermission() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        UserVersion manager = createUser(
                adminToken,
                testEmail("manager"),
                "整合測試營運主管"
        );
        manager = replaceRoles(
                adminToken,
                manager,
                "MANAGER",
                TEST_REASON_PREFIX + "assign-manager-role"
        );

        MvcResult managerLogin = performLogin(findUserEmail(manager.id()), "Test1234");
        String managerToken = JsonPath.read(responseBody(managerLogin), "$.accessToken");

        List<String> managerPermissions = JsonPath.read(
                responseBody(managerLogin),
                "$.user.permissions"
        );

        // 營運主管可以替代核准所有類型
        assertThat(managerPermissions).containsExactlyInAnyOrder(
                "CUSTOMER_VIEW",
                "CUSTOMER_APPROVE",
                "COMPANY_ITEM_VIEW",
                "COMPANY_ITEM_APPROVE",
                "CUSTOMER_ITEM_MAPPING_VIEW",
                "CUSTOMER_ITEM_MAPPING_APPROVE",
                "CUSTOMER_PRICE_VIEW",
                "CUSTOMER_PRICE_APPROVE",
                "SUPPLIER_VIEW",
                "SUPPLIER_APPROVE",
                "INVENTORY_VIEW",
                "INVENTORY_TRANSACTION_VIEW",
                "INVENTORY_RESERVATION_VIEW",
                "SO_VIEW",
                "SO_APPROVE",
                "SO_CANCELLATION_APPROVE"
        );

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearer(managerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        UserVersion ordinaryUser = createUser(
                adminToken,
                testEmail("ordinary"),
                "整合測試一般使用者"
        );
        String reason = TEST_REASON_PREFIX + "assign-reserved-app-admin";

        mockMvc.perform(put("/api/v1/users/{userId}/roles", ordinaryUser.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(
                                "APP_ADMIN",
                                reason,
                                ordinaryUser.version()
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("APP_ADMIN_AUTHORIZATION_IMMUTABLE"));

        mockMvc.perform(get("/api/v1/users/{userId}", ordinaryUser.id())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.version").value(ordinaryUser.version()));
    }

    @Test
    void staleVersionCannotOverwriteUserAuthorization() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UserVersion originalUser = createUser(
                accessToken,
                testEmail("version-conflict"),
                "整合測試版本衝突"
        );

        UserVersion updatedUser = replaceRoles(
                accessToken,
                originalUser,
                "SALES",
                TEST_REASON_PREFIX + "assign-role-before-conflict"
        );

        mockMvc.perform(put(
                                "/api/v1/users/{userId}/permission-grants",
                                originalUser.id()
                        )
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grants": [],
                                  "reason": "IT-stale-version",
                                  "version": %d
                                }
                                """.formatted(originalUser.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/users/{userId}", originalUser.id())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].roleCode").value("SALES"))
                .andExpect(jsonPath("$.permissionGrants").isEmpty())
                .andExpect(jsonPath("$.version").value(updatedUser.version()));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = performLogin(email, password);

        return JsonPath.read(responseBody(result), "$.accessToken");
    }

    private MvcResult performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
    }

    private UserVersion createUser(
            String accessToken,
            String email,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "name": "%s"
                                }
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // 用信中的連結設定密碼，帳號轉為啟用；設定密碼會改變版本號，所以重新讀取
        mockMvc.perform(post("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "Test1234"}
                                """.formatted(mailSender.latestTokenFor(email))))
                .andExpect(status().isNoContent());

        MvcResult activated = mockMvc.perform(get("/api/v1/users/{userId}", readLong(result, "$.id"))
                        .header("Authorization", bearer(accessToken)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        return readUserVersion(activated);
    }

    private UserVersion replaceRoles(
            String accessToken,
            UserVersion user,
            String roleCode,
            String reason
    ) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/users/{userId}/roles", user.id())
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(roleCode, reason, user.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].roleCode").value(roleCode))
                .andExpect(jsonPath("$.authorizationUpdatedAt").isNotEmpty())
                .andReturn();

        return readUserVersion(result);
    }

    private UserVersion replacePermissionGrants(
            String accessToken,
            UserVersion user
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        put("/api/v1/users/{userId}/permission-grants", user.id())
                                .header("Authorization", bearer(accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "grants": [
                                            {
                                              "permissionCode": "SUPPLIER_VIEW",
                                              "reason": "IT-temporary-grant",
                                              "validFrom": "2026-01-01T00:00:00Z",
                                              "validTo": null
                                            }
                                          ],
                                          "reason": "IT-replace-permission-grants",
                                          "version": %d
                                        }
                                        """.formatted(user.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionGrants[0].permission.permissionCode")
                        .value("SUPPLIER_VIEW"))
                // 業務角色沒有 SUPPLIER_VIEW，出現在有效權限中代表個別授權有生效
                .andExpect(jsonPath("$.effectivePermissions[*].permissionCode")
                        .value(hasItem("SUPPLIER_VIEW")))
                .andExpect(jsonPath("$.authorizationUpdatedAt").isNotEmpty())
                .andReturn();

        return readUserVersion(result);
    }

    private Long findUserVersion(String accessToken, Long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        return readLong(result, "$.version");
    }

    private String findUserEmail(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM app_user WHERE id = ?",
                String.class,
                userId
        );
    }

    private UserVersion readUserVersion(MvcResult result) throws Exception {
        return new UserVersion(
                readLong(result, "$.id"),
                readLong(result, "$.version")
        );
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(responseBody(result), path);

        return value.longValue();
    }

    private String responseBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private String testEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + TEST_EMAIL_SUFFIX;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String expiredToken() {
        Instant now = Instant.now();
        SecretKey signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(securityProperties.jwt().secret())
        );

        return Jwts.builder()
                .issuer(securityProperties.jwt().issuer())
                .subject("1")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(signingKey)
                .compact();
    }

    private String roleRequest(String roleCode, String reason, Long version) {
        return """
                {
                  "roleCodes": ["%s"],
                  "reason": "%s",
                  "version": %d
                }
                """.formatted(roleCode, reason, version);
    }

    //每一筆授權都帶一段較長的原因，讓稽核紀錄的內容夠長
    private String grantsRequest(List<String> permissionCodes, Long version) {
        String grants = permissionCodes.stream()
                .map(code -> """
                        {
                          "permissionCode": "%s",
                          "reason": "IT-many-grants 支援其他部門查詢資料，期間依主管指示調整",
                          "validFrom": "2026-01-01T00:00:00Z",
                          "validTo": null
                        }
                        """.formatted(code))
                .collect(Collectors.joining(","));

        return """
                {
                  "grants": [%s],
                  "reason": "IT-many-grants",
                  "version": %d
                }
                """.formatted(grants, version);
    }

    private record UserVersion(Long id, Long version) {
    }

    @Test
    void managerRolesContainAllPermissionsOfIncludedStaffRoles() {
        //RoleInclusions 說主管包含職員，初始化程式就必須真的把職員的權限都給主管
        for (String managerRole : List.of("SALES_MANAGER", "PURCHASING_MANAGER", "WAREHOUSE_MANAGER")) {
            for (String staffRole : RoleInclusions.includedRoleCodesOf(managerRole)) {
                assertThat(permissionCodesOf(managerRole))
                        .as("%s 應包含 %s 的全部權限", managerRole, staffRole)
                        .containsAll(permissionCodesOf(staffRole));
            }
        }
    }

    private List<String> permissionCodesOf(String roleCode) {
        return jdbcTemplate.queryForList(
                """
                        SELECT p.permission_code
                        FROM role_permission rp
                        JOIN role r ON r.id = rp.role_id
                        JOIN permission p ON p.id = rp.permission_id
                        WHERE r.role_code = ?
                        """,
                String.class,
                roleCode
        );
    }
}
