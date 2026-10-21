package com.smartdistribution.masterdata.supplier;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupplierApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@supplier.integration.test";
    private static final String SUPPLIER_NAME_PREFIX = "整合測試供應商-API-";
    private static final String PASSWORD = "Test1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> supplierIds = jdbcTemplate.queryForList(
                "SELECT id FROM supplier WHERE name LIKE ?",
                Long.class,
                SUPPLIER_NAME_PREFIX + "%"
        );

        for (Long supplierId : supplierIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'SUPPLIER' AND target_id = ?
                    )
                    """,
                    supplierId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'SUPPLIER' AND target_id = ?",
                    supplierId
            );
            jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE target_type = 'SUPPLIER' AND target_id = ?",
                    supplierId
            );
            jdbcTemplate.update("DELETE FROM supplier WHERE id = ?", supplierId);
        }

        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM app_user WHERE email LIKE ?",
                Long.class,
                "%" + TEST_EMAIL_SUFFIX
        );

        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void createGeneratesCodeAndNormalizesBlankFields() throws Exception {
        String purchasing = login(createUser("create", "PURCHASING"));

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  %s  ",
                                  "taxId": "",
                                  "contactName": "   ",
                                  "phone": "02-1234-5678"
                                }
                                """.formatted(supplierName())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierCode").value(matchesPattern("^SUP-\\d{6}$")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.taxId").value(nullValue()))
                .andExpect(jsonPath("$.contactName").value(nullValue()))
                .andExpect(jsonPath("$.phone").value("02-1234-5678"));
    }

    @Test
    void duplicateTaxIdIsRejected() throws Exception {
        String purchasing = login(createUser("tax-id", "PURCHASING"));
        String taxId = randomTaxId();

        createSupplier(purchasing, taxId);

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(supplierName(), taxId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("MASTER_DATA_TAX_ID_ALREADY_EXISTS"));
    }

    @Test
    void submittedSupplierBecomesActiveAfterApproval() throws Exception {
        String purchasing = login(createUser("flow-creator", "PURCHASING"));
        String manager = login(createUser("flow-approver", "PURCHASING_MANAGER"));

        SupplierVersion supplier = createSupplier(purchasing, null);
        submit(purchasing, supplier);

        mockMvc.perform(get("/api/v1/suppliers/{id}", supplier.id())
                        .header("Authorization", bearer(purchasing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        approve(manager, supplier.id());

        mockMvc.perform(get("/api/v1/suppliers/{id}", supplier.id())
                        .header("Authorization", bearer(purchasing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void pendingSupplierCannotBeUpdated() throws Exception {
        String purchasing = login(createUser("pending-update", "PURCHASING"));

        SupplierVersion supplier = submit(purchasing, createSupplier(purchasing, null));

        mockMvc.perform(patch("/api/v1/suppliers/{id}", supplier.id())
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(supplier.name(), null, "02-0000-0000", supplier.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_DATA_STATUS_CONFLICT"));
    }

    @Test
    void activeSupplierLocksIdentityButAllowsContactUpdate() throws Exception {
        String purchasing = login(createUser("active-update", "PURCHASING"));
        String manager = login(createUser("active-approver", "PURCHASING_MANAGER"));
        String taxId = randomTaxId();

        SupplierVersion supplier = createActiveSupplier(purchasing, manager, taxId);

        mockMvc.perform(patch("/api/v1/suppliers/{id}", supplier.id())
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(supplierName(), taxId, null, supplier.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_DATA_IDENTITY_LOCKED"));

        mockMvc.perform(patch("/api/v1/suppliers/{id}", supplier.id())
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(supplier.name(), taxId, "02-9999-9999", supplier.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.phone").value("02-9999-9999"));

        assertThat(countAudit(supplier.id(), "SUPPLIER_UPDATE")).isEqualTo(1);
    }

    @Test
    void managerCanDeactivateAndReactivateWithReason() throws Exception {
        String purchasing = login(createUser("status-creator", "PURCHASING"));
        String manager = login(createUser("status-manager", "PURCHASING_MANAGER"));

        SupplierVersion supplier = createActiveSupplier(purchasing, manager, null);

        mockMvc.perform(post("/api/v1/suppliers/{id}/deactivate", supplier.id())
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(" ", supplier.version())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        MvcResult deactivated = mockMvc.perform(
                        post("/api/v1/suppliers/{id}/deactivate", supplier.id())
                                .header("Authorization", bearer(manager))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusBody("供應商停止營業", supplier.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn();

        mockMvc.perform(post("/api/v1/suppliers/{id}/activate", supplier.id())
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("恢復往來", readVersion(deactivated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(countAudit(supplier.id(), "SUPPLIER_DEACTIVATE")).isEqualTo(1);
        assertThat(countAudit(supplier.id(), "SUPPLIER_ACTIVATE")).isEqualTo(1);
    }

    @Test
    void operationsRequireMatchingPermission() throws Exception {
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String purchasing = login(createUser("no-status-manage", "PURCHASING"));
        String manager = login(createUser("permission-approver", "PURCHASING_MANAGER"));

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", bearer(warehouse))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(supplierName(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        SupplierVersion supplier = createActiveSupplier(purchasing, manager, null);

        mockMvc.perform(post("/api/v1/suppliers/{id}/deactivate", supplier.id())
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("採購不能停用", supplier.version())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private SupplierVersion createActiveSupplier(
            String creatorToken,
            String approverToken,
            String taxId
    ) throws Exception {
        SupplierVersion supplier = submit(creatorToken, createSupplier(creatorToken, taxId));
        approve(approverToken, supplier.id());

        MvcResult result = mockMvc.perform(get("/api/v1/suppliers/{id}", supplier.id())
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        return new SupplierVersion(supplier.id(), supplier.name(), readVersion(result));
    }

    private SupplierVersion createSupplier(String token, String taxId) throws Exception {
        String name = supplierName();

        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, taxId)))
                .andExpect(status().isCreated())
                .andReturn();

        return new SupplierVersion(readId(result), name, readVersion(result));
    }

    private SupplierVersion submit(String token, SupplierVersion supplier) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/suppliers/{id}/submit", supplier.id())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"version": %d}
                                        """.formatted(supplier.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        return new SupplierVersion(supplier.id(), supplier.name(), readVersion(result));
    }

    private void approve(String token, Long supplierId) throws Exception {
        Long approvalRequestId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'SUPPLIER' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                supplierId
        );

        mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private int countAudit(Long supplierId, String actionType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE target_type = 'SUPPLIER' AND target_id = ? AND action_type = ?
                """,
                Integer.class,
                supplierId,
                actionType
        );

        return count == null ? 0 : count;
    }

    private String createBody(String name, String taxId) {
        return """
                {
                  "name": "%s",
                  "taxId": %s
                }
                """.formatted(name, jsonString(taxId));
    }

    private String updateBody(String name, String taxId, String phone, Long version) {
        return """
                {
                  "name": "%s",
                  "taxId": %s,
                  "contactName": null,
                  "phone": %s,
                  "email": null,
                  "address": null,
                  "version": %d
                }
                """.formatted(name, jsonString(taxId), jsonString(phone), version);
    }

    private String statusBody(String reason, Long version) {
        return """
                {
                  "reason": "%s",
                  "version": %d
                }
                """.formatted(reason, version);
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String supplierName() {
        return SUPPLIER_NAME_PREFIX + UUID.randomUUID();
    }

    private String randomTaxId() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000));
    }

    private AppUser createUser(String prefix, String roleCode) {
        AppUser user = userRepository.save(new AppUser(
                prefix + "-" + UUID.randomUUID() + TEST_EMAIL_SUFFIX,
                prefix,
                passwordEncoder.encode(PASSWORD)
        ));
        Role role = roleRepository.findByRoleCode(roleCode).orElseThrow();
        userRoleRepository.save(new UserRole(user, role));

        return user;
    }

    private String login(AppUser user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private Long readId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private Long readVersion(MvcResult result) throws Exception {
        Number version = JsonPath.read(result.getResponse().getContentAsString(), "$.version");
        return version.longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record SupplierVersion(Long id, String name, Long version) {
    }
}
