package com.smartdistribution.identity;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.shared.security.config.SecurityProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(managerPermissions).containsExactlyInAnyOrder(
                "CUSTOMER_APPROVE",
                "SUPPLIER_APPROVE"
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
                                  "name": "%s",
                                  "password": "Test1234"
                                }
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        return readUserVersion(result);
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
                                              "permissionCode": "SO_CREATE",
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
                        .value("SO_CREATE"))
                .andExpect(jsonPath("$.effectivePermissions[0].permissionCode")
                        .value("SO_CREATE"))
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

    private record UserVersion(Long id, Long version) {
    }
}
