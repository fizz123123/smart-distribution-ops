package com.smartdistribution.masterdata.companyitem;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.masterdata.supplier.repository.SupplierRepository;
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
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyItemApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@company-item.integration.test";
    private static final String ITEM_CODE_PREFIX = "ITTEST-";
    private static final String SUPPLIER_NAME_PREFIX = "整合測試供應商-料號-";
    private static final String ITEM_NAME = "整合測試 PE 保溫管 1/2 吋 × 10mm";
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
    private SupplierRepository supplierRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> itemIds = jdbcTemplate.queryForList(
                "SELECT id FROM company_item WHERE item_code LIKE ?",
                Long.class,
                ITEM_CODE_PREFIX + "%"
        );

        for (Long itemId : itemIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'COMPANY_ITEM' AND target_id = ?
                    )
                    """,
                    itemId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'COMPANY_ITEM' AND target_id = ?",
                    itemId
            );
            jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE target_type = 'COMPANY_ITEM' AND target_id = ?",
                    itemId
            );
            jdbcTemplate.update("DELETE FROM item_unit_conversion WHERE company_item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM inventory_policy WHERE company_item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM inventory_balance WHERE company_item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM company_item WHERE id = ?", itemId);
        }

        jdbcTemplate.update("DELETE FROM supplier WHERE name LIKE ?", SUPPLIER_NAME_PREFIX + "%");

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
    void createNormalizesCodeAndInitializesInventory() throws Exception {
        String sales = login(createUser("create", "SALES"));
        String code = randomCode();

        MvcResult result = mockMvc.perform(post("/api/v1/company-items")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("  " + code.toLowerCase(Locale.ROOT) + "  ", "STICK")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemCode").value(code))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.unitConversions.length()").value(1))
                .andExpect(jsonPath("$.unitConversions[0].unit").value("STICK"))
                .andExpect(jsonPath("$.unitConversions[0].factor").value(1))
                .andExpect(jsonPath("$.unitConversions[0].baseUnit").value(true))
                .andExpect(jsonPath("$.inventoryPolicy.safetyStockBaseQty").value(0))
                .andReturn();

        Integer balanceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_balance WHERE company_item_id = ?",
                Integer.class,
                readLong(result, "$.id")
        );
        assertThat(balanceCount).isEqualTo(1);
    }

    @Test
    void duplicateCodeIsRejectedRegardlessOfCase() throws Exception {
        String sales = login(createUser("duplicate", "SALES"));
        String code = randomCode();

        createItem(sales, code, "STICK");

        mockMvc.perform(post("/api/v1/company-items")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(code.toLowerCase(Locale.ROOT), "STICK")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ITEM_CODE_ALREADY_EXISTS"));
    }

    @Test
    void managerCanApproveButWarehouseCannot() throws Exception {
        String sales = login(createUser("approve-creator", "SALES"));
        String warehouse = login(createUser("approve-warehouse", "WAREHOUSE"));
        String manager = login(createUser("approve-manager", "MANAGER"));

        ItemVersion item = submit(sales, createItem(sales, randomCode(), "STICK"));
        Long approvalRequestId = findPendingApprovalId(item.id());

        mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                        .header("Authorization", bearer(warehouse))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 營運主管可以替代核准所有類型，包含公司料號
        mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company-items/{id}", item.id())
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void draftUnitConversionsAreReplacedAndBaseUnitIsRejected() throws Exception {
        String sales = login(createUser("conversion", "SALES"));

        ItemVersion item = createItem(sales, randomCode(), "STICK");

        MvcResult result = mockMvc.perform(
                        put("/api/v1/company-items/{id}/unit-conversions", item.id())
                                .header("Authorization", bearer(sales))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(conversionsBody(
                                        "{\"unit\": \"PKG\", \"factor\": 5}, {\"unit\": \"BOX\", \"factor\": 10}",
                                        item.version()
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitConversions.length()").value(3))
                .andExpect(jsonPath("$.unitConversions[1].unit").value("BOX"))
                .andExpect(jsonPath("$.unitConversions[2].unit").value("PKG"))
                .andReturn();

        assertThat(readLong(result, "$.version")).isGreaterThan(item.version());

        mockMvc.perform(put("/api/v1/company-items/{id}/unit-conversions", item.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversionsBody(
                                "{\"unit\": \"STICK\", \"factor\": 2}",
                                readLong(result, "$.version")
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPANY_ITEM_UNIT_CONVERSION_INVALID"));

        // 已有換算時，草稿也不能改基準單位
        mockMvc.perform(patch("/api/v1/company-items/{id}", item.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(item.code(), ITEM_NAME, "PCS", null, readLong(result, "$.version"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ITEM_UNIT_CONVERSIONS_EXIST"));
    }

    @Test
    void activeItemLocksIdentityButAllowsSupplierAndConversionChanges() throws Exception {
        String sales = login(createUser("active-creator", "SALES"));
        String salesManager = login(createUser("active-approver", "SALES_MANAGER"));
        Supplier supplier = createActiveSupplier();

        ItemVersion item = createActiveItem(sales, salesManager);

        mockMvc.perform(patch("/api/v1/company-items/{id}", item.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(item.code(), ITEM_NAME + " 改名", "STICK", null, item.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_DATA_IDENTITY_LOCKED"));

        MvcResult updated = mockMvc.perform(patch("/api/v1/company-items/{id}", item.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(item.code(), ITEM_NAME, "STICK", supplier.getId(), item.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSupplier.id").value(supplier.getId()))
                .andReturn();

        mockMvc.perform(put("/api/v1/company-items/{id}/unit-conversions", item.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversionsBody(
                                "{\"unit\": \"BOX\", \"factor\": 20}",
                                readLong(updated, "$.version")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(countAudit(item.id(), "COMPANY_ITEM_UPDATE")).isEqualTo(1);
        assertThat(countAudit(item.id(), "COMPANY_ITEM_UNIT_CONVERSIONS_REPLACE")).isEqualTo(1);
    }

    @Test
    void onlyPurchasingManagerCanChangeSafetyStock() throws Exception {
        String sales = login(createUser("policy-sales", "SALES"));
        String purchasingManager = login(createUser("policy-manager", "PURCHASING_MANAGER"));

        ItemVersion item = createItem(sales, randomCode(), "STICK");

        MvcResult policy = mockMvc.perform(
                        get("/api/v1/company-items/{id}/inventory-policy", item.id())
                                .header("Authorization", bearer(purchasingManager)))
                .andExpect(status().isOk())
                .andReturn();
        Long policyVersion = readLong(policy, "$.version");

        mockMvc.perform(put("/api/v1/company-items/{id}/inventory-policy", item.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("50", policyVersion)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/company-items/{id}/inventory-policy", item.id())
                        .header("Authorization", bearer(purchasingManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("50", policyVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safetyStockBaseQty").value(50));

        assertThat(countAudit(item.id(), "INVENTORY_POLICY_UPDATE")).isEqualTo(1);
    }

    private ItemVersion createActiveItem(String creatorToken, String approverToken) throws Exception {
        ItemVersion item = submit(creatorToken, createItem(creatorToken, randomCode(), "STICK"));

        mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", findPendingApprovalId(item.id()))
                        .header("Authorization", bearer(approverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/company-items/{id}", item.id())
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        return new ItemVersion(item.id(), item.code(), readLong(result, "$.version"));
    }

    private ItemVersion createItem(String token, String code, String baseUnit) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/company-items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(code, baseUnit)))
                .andExpect(status().isCreated())
                .andReturn();

        return new ItemVersion(readLong(result, "$.id"), code, readLong(result, "$.version"));
    }

    private ItemVersion submit(String token, ItemVersion item) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/company-items/{id}/submit", item.id())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d}
                                """.formatted(item.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        return new ItemVersion(item.id(), item.code(), readLong(result, "$.version"));
    }

    private Long findPendingApprovalId(Long itemId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'COMPANY_ITEM' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                itemId
        );
    }

    // 偏好供應商必須已啟用；直接透過 entity 建立，不經過供應商的核准流程
    private Supplier createActiveSupplier() {
        Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
                SUPPLIER_NAME_PREFIX + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                1L
        ));
        supplier.assignCode();
        supplier.submit();
        supplier.approve();

        return supplierRepository.saveAndFlush(supplier);
    }

    private int countAudit(Long itemId, String actionType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE target_type = 'COMPANY_ITEM' AND target_id = ? AND action_type = ?
                """,
                Integer.class,
                itemId,
                actionType
        );

        return count == null ? 0 : count;
    }

    private String createBody(String code, String baseUnit) {
        return """
                {
                  "itemCode": "%s",
                  "name": "%s",
                  "baseUnit": "%s"
                }
                """.formatted(code, ITEM_NAME, baseUnit);
    }

    private String updateBody(
            String code,
            String name,
            String baseUnit,
            Long preferredSupplierId,
            Long version
    ) {
        return """
                {
                  "itemCode": "%s",
                  "name": "%s",
                  "baseUnit": "%s",
                  "preferredSupplierId": %s,
                  "version": %d
                }
                """.formatted(code, name, baseUnit, preferredSupplierId, version);
    }

    private String conversionsBody(String conversions, Long version) {
        return """
                {
                  "unitConversions": [%s],
                  "version": %d
                }
                """.formatted(conversions, version);
    }

    private String policyBody(String safetyStock, Long version) {
        return """
                {
                  "safetyStockBaseQty": %s,
                  "reason": "旺季前提高安全庫存",
                  "version": %d
                }
                """.formatted(safetyStock, version);
    }

    private String randomCode() {
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

    private record ItemVersion(Long id, String code, Long version) {
    }
}
