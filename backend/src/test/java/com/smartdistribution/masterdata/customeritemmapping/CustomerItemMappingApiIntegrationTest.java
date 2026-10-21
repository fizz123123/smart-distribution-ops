package com.smartdistribution.masterdata.customeritemmapping;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerItemMappingApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@mapping.integration.test";
    private static final String CUSTOMER_NAME_PREFIX = "整合測試客戶-對應-";
    private static final String ITEM_CODE_PREFIX = "MAPTEST-";
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
    private CustomerRepository customerRepository;

    @Autowired
    private CompanyItemRepository companyItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> mappingIds = jdbcTemplate.queryForList(
                """
                SELECT mapping.id
                FROM customer_item_mapping mapping
                JOIN customer customer ON customer.id = mapping.customer_id
                WHERE customer.name LIKE ?
                """,
                Long.class,
                CUSTOMER_NAME_PREFIX + "%"
        );

        for (Long mappingId : mappingIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'CUSTOMER_ITEM_MAPPING' AND target_id = ?
                    )
                    """,
                    mappingId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'CUSTOMER_ITEM_MAPPING' AND target_id = ?",
                    mappingId
            );
            jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE target_type = 'CUSTOMER_ITEM_MAPPING' AND target_id = ?",
                    mappingId
            );
            jdbcTemplate.update("DELETE FROM customer_item_mapping WHERE id = ?", mappingId);
        }

        jdbcTemplate.update("DELETE FROM customer WHERE name LIKE ?", CUSTOMER_NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM company_item WHERE item_code LIKE ?", ITEM_CODE_PREFIX + "%");

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
    void blankCustomerItemCodeDefaultsToCompanyItemCode() throws Exception {
        String sales = login(createUser("default-code", "SALES"));
        Customer customer = createActiveCustomer();
        CompanyItem item = createActiveItem();

        mockMvc.perform(post("/api/v1/customer-item-mappings")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customer.getId(), "  ", item.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerItemCode").value(item.getItemCode()))
                .andExpect(jsonPath("$.customer.customerCode").value(customer.getCustomerCode()))
                .andExpect(jsonPath("$.companyItem.itemCode").value(item.getItemCode()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void sameCustomerItemCodeCannotBeMappedTwice() throws Exception {
        String sales = login(createUser("duplicate", "SALES"));
        Customer customer = createActiveCustomer();
        CompanyItem item = createActiveItem();
        CompanyItem otherItem = createActiveItem();

        createMapping(sales, customer.getId(), "A-100", item.getId());

        mockMvc.perform(post("/api/v1/customer-item-mappings")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customer.getId(), "A-100", otherItem.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ITEM_MAPPING_ALREADY_EXISTS"));
    }

    @Test
    void referencesMustBeActive() throws Exception {
        String sales = login(createUser("inactive-reference", "SALES"));
        Customer customer = createActiveCustomer();
        CompanyItem draftItem = companyItemRepository.saveAndFlush(new CompanyItem(
                randomItemCode(),
                "整合測試草稿料號",
                UnitOfMeasure.PCS,
                null,
                1L
        ));

        mockMvc.perform(post("/api/v1/customer-item-mappings")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customer.getId(), "A-200", draftItem.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE"));
    }

    @Test
    void onlySalesManagerCanApprove() throws Exception {
        String sales = login(createUser("approve-creator", "SALES"));
        String manager = login(createUser("approve-manager", "MANAGER"));
        String salesManager = login(createUser("approve-sales-manager", "SALES_MANAGER"));

        MappingVersion mapping = submit(sales, createMapping(
                sales,
                createActiveCustomer().getId(),
                "A-300",
                createActiveItem().getId()
        ));
        Long approvalRequestId = findPendingApprovalId(mapping.id());

        // 客戶料號對應不開放 MANAGER 核准（D-040）
        approve(manager, approvalRequestId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        approve(salesManager, approvalRequestId).andExpect(status().isOk());

        assertThat(mappingStatus(mapping.id())).isEqualTo("ACTIVE");
    }

    @Test
    void approvalIsBlockedWhenCompanyItemWasDeactivated() throws Exception {
        String sales = login(createUser("blocked-creator", "SALES"));
        String salesManager = login(createUser("blocked-approver", "SALES_MANAGER"));
        CompanyItem item = createActiveItem();

        MappingVersion mapping = submit(sales, createMapping(
                sales,
                createActiveCustomer().getId(),
                "A-400",
                item.getId()
        ));

        // 待核准期間公司料號被停用
        CompanyItem storedItem = companyItemRepository.findById(item.getId()).orElseThrow();
        storedItem.deactivate();
        companyItemRepository.saveAndFlush(storedItem);

        approve(salesManager, findPendingApprovalId(mapping.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE"));

        // 核准整筆回滾：對應仍待核准，申請也仍是 PENDING
        assertThat(mappingStatus(mapping.id())).isEqualTo("PENDING_APPROVAL");
        assertThat(findPendingApprovalId(mapping.id())).isNotNull();
    }

    @Test
    void activeMappingCannotBeUpdatedButCanBeDeactivatedAndReactivated() throws Exception {
        String sales = login(createUser("active-creator", "SALES"));
        String salesManager = login(createUser("active-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer();
        CompanyItem item = createActiveItem();

        MappingVersion mapping = submit(sales, createMapping(sales, customer.getId(), "A-500", item.getId()));
        approve(salesManager, findPendingApprovalId(mapping.id())).andExpect(status().isOk());
        Long activeVersion = mappingVersion(mapping.id());

        mockMvc.perform(patch("/api/v1/customer-item-mappings/{id}", mapping.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "customerItemCode": "A-500",
                                  "companyItemId": %d,
                                  "version": %d
                                }
                                """.formatted(customer.getId(), item.getId(), activeVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_DATA_IDENTITY_LOCKED"));

        MvcResult deactivated = mockMvc.perform(
                        post("/api/v1/customer-item-mappings/{id}/deactivate", mapping.id())
                                .header("Authorization", bearer(salesManager))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusBody("客戶停用此料號", activeVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn();

        mockMvc.perform(post("/api/v1/customer-item-mappings/{id}/activate", mapping.id())
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("客戶恢復使用", readLong(deactivated, "$.version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(countAudit(mapping.id(), "CUSTOMER_ITEM_MAPPING_DEACTIVATE")).isEqualTo(1);
        assertThat(countAudit(mapping.id(), "CUSTOMER_ITEM_MAPPING_ACTIVATE")).isEqualTo(1);
    }

    private MappingVersion createMapping(
            String token,
            Long customerId,
            String customerItemCode,
            Long companyItemId
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customer-item-mappings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customerId, customerItemCode, companyItemId)))
                .andExpect(status().isCreated())
                .andReturn();

        return new MappingVersion(readLong(result, "$.id"), readLong(result, "$.version"));
    }

    private MappingVersion submit(String token, MappingVersion mapping) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/customer-item-mappings/{id}/submit", mapping.id())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"version": %d}
                                        """.formatted(mapping.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        return new MappingVersion(mapping.id(), readLong(result, "$.version"));
    }

    private ResultActions approve(
            String token,
            Long approvalRequestId
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private Long findPendingApprovalId(Long mappingId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'CUSTOMER_ITEM_MAPPING' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                mappingId
        );
    }

    private String mappingStatus(Long mappingId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM customer_item_mapping WHERE id = ?",
                String.class,
                mappingId
        );
    }

    private Long mappingVersion(Long mappingId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM customer_item_mapping WHERE id = ?",
                Long.class,
                mappingId
        );
    }

    // 引用的客戶與公司料號必須已啟用；直接透過 entity 建立，不經過各自的核准流程
    private Customer createActiveCustomer() {
        Customer customer = customerRepository.saveAndFlush(new Customer(
                CUSTOMER_NAME_PREFIX + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                1L
        ));
        customer.assignCode();
        customer.submit();
        customer.approve();

        return customerRepository.saveAndFlush(customer);
    }

    private CompanyItem createActiveItem() {
        CompanyItem item = companyItemRepository.saveAndFlush(new CompanyItem(
                randomItemCode(),
                "整合測試料號",
                UnitOfMeasure.PCS,
                null,
                1L
        ));
        item.submit();
        item.approve();

        return companyItemRepository.saveAndFlush(item);
    }

    private int countAudit(Long mappingId, String actionType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE target_type = 'CUSTOMER_ITEM_MAPPING' AND target_id = ? AND action_type = ?
                """,
                Integer.class,
                mappingId,
                actionType
        );

        return count == null ? 0 : count;
    }

    private String createBody(Long customerId, String customerItemCode, Long companyItemId) {
        return """
                {
                  "customerId": %d,
                  "customerItemCode": "%s",
                  "companyItemId": %d
                }
                """.formatted(customerId, customerItemCode, companyItemId);
    }

    private String statusBody(String reason, Long version) {
        return """
                {
                  "reason": "%s",
                  "version": %d
                }
                """.formatted(reason, version);
    }

    private String randomItemCode() {
        return ITEM_CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
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

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record MappingVersion(Long id, Long version) {
    }
}
