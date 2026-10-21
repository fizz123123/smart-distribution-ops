package com.smartdistribution.masterdata.customer;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@customer.integration.test";
    private static final String CUSTOMER_NAME_PREFIX = "整合測試客戶-API-";
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
        List<Long> customerIds = jdbcTemplate.queryForList(
                "SELECT id FROM customer WHERE name LIKE ?",
                Long.class,
                CUSTOMER_NAME_PREFIX + "%"
        );

        for (Long customerId : customerIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'CUSTOMER' AND target_id = ?
                    )
                    """,
                    customerId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'CUSTOMER' AND target_id = ?",
                    customerId
            );
            jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE target_type = 'CUSTOMER' AND target_id = ?",
                    customerId
            );
            jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
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
        String sales = login(createUser("create", "SALES"));

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  %s  ",
                                  "taxId": "",
                                  "contactName": "   ",
                                  "phone": "02-1234-5678"
                                }
                                """.formatted(customerName())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerCode").value(matchesPattern("^CUS-\\d{6}$")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.paymentTerm").value("DUE_0"))
                .andExpect(jsonPath("$.taxId").value(nullValue()))
                .andExpect(jsonPath("$.contactName").value(nullValue()))
                .andExpect(jsonPath("$.phone").value("02-1234-5678"));
    }

    @Test
    void duplicateTaxIdIsRejected() throws Exception {
        String sales = login(createUser("tax-id", "SALES"));
        String taxId = randomTaxId();

        createCustomer(sales, taxId);

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customerName(), taxId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("MASTER_DATA_TAX_ID_ALREADY_EXISTS"));
    }

    @Test
    void submittedCustomerBecomesActiveAfterApproval() throws Exception {
        String sales = login(createUser("flow-creator", "SALES"));
        String manager = login(createUser("flow-approver", "SALES_MANAGER"));

        CustomerVersion customer = createCustomer(sales, null);
        submit(sales, customer);

        mockMvc.perform(get("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        approve(manager, customer.id());

        mockMvc.perform(get("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void pendingCustomerCannotBeUpdated() throws Exception {
        String sales = login(createUser("pending-update", "SALES"));

        CustomerVersion customer = submit(sales, createCustomer(sales, null));

        mockMvc.perform(patch("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer.name(), null, "02-0000-0000", customer.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_DATA_STATUS_CONFLICT"));
    }

    @Test
    void activeCustomerLocksIdentityButAllowsContactUpdate() throws Exception {
        String sales = login(createUser("active-update", "SALES"));
        String manager = login(createUser("active-approver", "SALES_MANAGER"));
        String taxId = randomTaxId();

        CustomerVersion customer = createActiveCustomer(sales, manager, taxId);

        mockMvc.perform(patch("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customerName(), taxId, null, customer.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_DATA_IDENTITY_LOCKED"));

        mockMvc.perform(patch("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer.name(), taxId, "02-9999-9999", customer.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.phone").value("02-9999-9999"));

        assertThat(countAudit(customer.id(), "CUSTOMER_UPDATE")).isEqualTo(1);
    }

    @Test
    void managerCanDeactivateAndReactivateWithReason() throws Exception {
        String sales = login(createUser("status-creator", "SALES"));
        String manager = login(createUser("status-manager", "SALES_MANAGER"));

        CustomerVersion customer = createActiveCustomer(sales, manager, null);

        mockMvc.perform(post("/api/v1/customers/{id}/deactivate", customer.id())
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(" ", customer.version())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        MvcResult deactivated = mockMvc.perform(
                        post("/api/v1/customers/{id}/deactivate", customer.id())
                                .header("Authorization", bearer(manager))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusBody("客戶停止營業", customer.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn();

        mockMvc.perform(post("/api/v1/customers/{id}/activate", customer.id())
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("恢復往來", readVersion(deactivated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(countAudit(customer.id(), "CUSTOMER_DEACTIVATE")).isEqualTo(1);
        assertThat(countAudit(customer.id(), "CUSTOMER_ACTIVATE")).isEqualTo(1);
    }

    @Test
    void operationsRequireMatchingPermission() throws Exception {
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String sales = login(createUser("no-status-manage", "SALES"));
        String manager = login(createUser("permission-approver", "SALES_MANAGER"));

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(warehouse))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customerName(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        CustomerVersion customer = createActiveCustomer(sales, manager, null);

        mockMvc.perform(post("/api/v1/customers/{id}/deactivate", customer.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("業務不能停用", customer.version())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void managerCanApproveCustomer() throws Exception {
        String sales = login(createUser("manager-flow-creator", "SALES"));
        String manager = login(createUser("manager-flow-approver", "MANAGER"));

        CustomerVersion customer = submit(sales, createCustomer(sales, null));
        approve(manager, customer.id());

        mockMvc.perform(get("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void financeManagerCanChangePaymentTermInAnyStatus() throws Exception {
        String sales = login(createUser("term-creator", "SALES"));
        String finance = login(createUser("term-finance", "FINANCE_MANAGER"));

        CustomerVersion customer = createCustomer(sales, null);

        mockMvc.perform(put("/api/v1/customers/{id}/payment-term", customer.id())
                        .header("Authorization", bearer(finance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentTermBody("NET_30", customer.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.paymentTerm").value("NET_30"));

        assertThat(countAudit(customer.id(), "CUSTOMER_PAYMENT_TERM_CHANGE")).isEqualTo(1);
    }

    @Test
    void salesCannotChangePaymentTerm() throws Exception {
        String sales = login(createUser("term-sales", "SALES"));

        CustomerVersion customer = createCustomer(sales, null);

        mockMvc.perform(put("/api/v1/customers/{id}/payment-term", customer.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentTermBody("NET_60", customer.version())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String paymentTermBody(String paymentTerm, Long version) {
        return """
                {
                  "paymentTerm": "%s",
                  "reason": "依核定交易條件調整",
                  "version": %d
                }
                """.formatted(paymentTerm, version);
    }

    private CustomerVersion createActiveCustomer(
            String creatorToken,
            String approverToken,
            String taxId
    ) throws Exception {
        CustomerVersion customer = submit(creatorToken, createCustomer(creatorToken, taxId));
        approve(approverToken, customer.id());

        MvcResult result = mockMvc.perform(get("/api/v1/customers/{id}", customer.id())
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        return new CustomerVersion(customer.id(), customer.name(), readVersion(result));
    }

    private CustomerVersion createCustomer(String token, String taxId) throws Exception {
        String name = customerName();

        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, taxId)))
                .andExpect(status().isCreated())
                .andReturn();

        return new CustomerVersion(readId(result), name, readVersion(result));
    }

    private CustomerVersion submit(String token, CustomerVersion customer) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/customers/{id}/submit", customer.id())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"version": %d}
                                        """.formatted(customer.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        return new CustomerVersion(customer.id(), customer.name(), readVersion(result));
    }

    private void approve(String token, Long customerId) throws Exception {
        Long approvalRequestId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'CUSTOMER' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                customerId
        );

        mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private int countAudit(Long customerId, String actionType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE target_type = 'CUSTOMER' AND target_id = ? AND action_type = ?
                """,
                Integer.class,
                customerId,
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

    private String customerName() {
        return CUSTOMER_NAME_PREFIX + UUID.randomUUID();
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

    private record CustomerVersion(Long id, String name, Long version) {
    }
}
