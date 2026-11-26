package com.smartdistribution.pricing;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StandardPriceApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@standard-price.integration.test";
    private static final String ITEM_CODE_PREFIX = "STDTEST-";
    private static final String PASSWORD = "Test1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //和正式程式用同一個時鐘取「今天」
    @Autowired
    private Clock clock;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private CompanyItemRepository companyItemRepository;

    @Autowired
    private ItemUnitConversionRepository itemUnitConversionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> priceIds = jdbcTemplate.queryForList(
                """
                SELECT price.id
                FROM standard_price price
                JOIN company_item item ON item.id = price.company_item_id
                WHERE item.item_code LIKE ?
                """,
                Long.class,
                ITEM_CODE_PREFIX + "%"
        );

        for (Long priceId : priceIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'STANDARD_PRICE' AND target_id = ?
                    )
                    """,
                    priceId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'STANDARD_PRICE' AND target_id = ?",
                    priceId
            );
            jdbcTemplate.update("DELETE FROM standard_price WHERE id = ?", priceId);
        }

        jdbcTemplate.update(
                """
                DELETE conversion FROM item_unit_conversion conversion
                JOIN company_item item ON item.id = conversion.company_item_id
                WHERE item.item_code LIKE ?
                """,
                ITEM_CODE_PREFIX + "%"
        );
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
    void createdPriceGoesStraightToReviewAndOnlyOnePendingPerUnit() throws Exception {
        String sales = login(createUser("create", "SALES"));
        CompanyItem item = createActiveItem();

        Long priceId = createPrice(sales, item.getId(), "PCS", "100.00");

        assertThat(priceStatus(priceId)).isEqualTo("PENDING_REVIEW");
        assertThat(findPendingApprovalId(priceId)).isNotNull();

        performCreate(sales, item.getId(), "PCS", "105.00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STANDARD_PRICE_PENDING_EXISTS"));

        //箱是已設定換算的單位，可以另外一筆；公斤沒有換算
        performCreate(sales, item.getId(), "BOX", "950.00", null)
                .andExpect(status().isCreated());
        performCreate(sales, item.getId(), "KG", "50.00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STANDARD_PRICE_UNIT_INVALID"));
    }

    @Test
    void approvedPriceReplacesCurrentPriceAndCanBeRevoked() throws Exception {
        String sales = login(createUser("replace-sales", "SALES"));
        String salesManager = login(createUser("replace-manager", "SALES_MANAGER"));
        CompanyItem item = createActiveItem();
        LocalDate today = LocalDate.now(clock);

        Long firstId = createPrice(sales, item.getId(), "PCS", "100.00");
        approve(salesManager, findPendingApprovalId(firstId)).andExpect(status().isOk());

        Long secondId = createPrice(sales, item.getId(), "PCS", "110.00", today.plusDays(7));
        Long secondApprovalId = findPendingApprovalId(secondId);

        mockMvc.perform(get("/api/v1/approval-requests/{id}", secondApprovalId)
                        .header("Authorization", bearer(salesManager)))
                .andExpect(jsonPath("$.request.targetLabel").value(containsString("標準售價 110.00")))
                .andExpect(jsonPath("$.warnings[0]").value(containsString("於 " + today.plusDays(6) + " 結束")));

        approve(salesManager, secondApprovalId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/standard-prices/{id}", firstId)
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPLACED"))
                .andExpect(jsonPath("$.effectiveTo").value(today.plusDays(6).toString()));

        //業務不能撤銷，業務主管可以
        mockMvc.perform(post("/api/v1/standard-prices/{id}/revoke", secondId)
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeBody(priceVersion(secondId))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/standard-prices/{id}/revoke", secondId)
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeBody(priceVersion(secondId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void periodRulesMatchCustomerPrices() throws Exception {
        String sales = login(createUser("period-sales", "SALES"));
        String salesManager = login(createUser("period-manager", "SALES_MANAGER"));
        CompanyItem item = createActiveItem();
        LocalDate today = LocalDate.now(clock);

        performCreate(sales, item.getId(), "PCS", "100.00", today.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_INVALID"));

        Long scheduledId = createPrice(sales, item.getId(), "PCS", "100.00", today.plusDays(10));
        approve(salesManager, findPendingApprovalId(scheduledId)).andExpect(status().isOk());

        performCreate(sales, item.getId(), "PCS", "90.00", today.plusDays(5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_CONFLICT"));

        //和最後一個版本同一天也不行
        performCreate(sales, item.getId(), "PCS", "90.00", today.plusDays(10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_CONFLICT"));
    }

    @Test
    void permissionsFollowRoles() throws Exception {
        String sales = login(createUser("perm-sales", "SALES"));
        String manager = login(createUser("perm-manager", "MANAGER"));
        String warehouse = login(createUser("perm-warehouse", "WAREHOUSE"));
        String purchasing = login(createUser("perm-purchasing", "PURCHASING"));
        CompanyItem item = createActiveItem();

        //採購、倉管沒有標準售價的權限
        performCreate(purchasing, item.getId(), "PCS", "100.00", null)
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/standard-prices")
                        .header("Authorization", bearer(warehouse)))
                .andExpect(status().isForbidden());

        //營運主管可以替代核准，但不能建立
        Long priceId = createPrice(sales, item.getId(), "PCS", "100.00");
        performCreate(manager, item.getId(), "BOX", "950.00", null)
                .andExpect(status().isForbidden());
        approve(manager, findPendingApprovalId(priceId)).andExpect(status().isOk());

        assertThat(priceStatus(priceId)).isEqualTo("ACTIVE");
    }

    @Test
    void itemMustBeActive() throws Exception {
        String sales = login(createUser("inactive-item", "SALES"));
        CompanyItem item = createActiveItem();

        CompanyItem stored = companyItemRepository.findById(item.getId()).orElseThrow();
        stored.deactivate();
        companyItemRepository.saveAndFlush(stored);

        performCreate(sales, item.getId(), "PCS", "100.00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STANDARD_PRICE_ITEM_INACTIVE"));
    }

    private Long createPrice(String token, Long itemId, String unit, String price) throws Exception {
        return createPrice(token, itemId, unit, price, null);
    }

    private Long createPrice(String token, Long itemId, String unit, String price, LocalDate effectiveFrom)
            throws Exception {
        MvcResult result = performCreate(token, itemId, unit, price, effectiveFrom)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private ResultActions performCreate(
            String token,
            Long itemId,
            String unit,
            String price,
            LocalDate effectiveFrom
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/standard-prices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "companyItemId": %d,
                          "pricingUnit": "%s",
                          "unitPriceExcludingTax": %s,
                          "effectiveFrom": %s
                        }
                        """.formatted(
                        itemId,
                        unit,
                        price,
                        effectiveFrom == null ? "null" : "\"" + effectiveFrom + "\""
                )));
    }

    private ResultActions approve(String token, Long approvalRequestId) throws Exception {
        return mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private Long findPendingApprovalId(Long priceId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'STANDARD_PRICE' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                priceId
        );
    }

    private String priceStatus(Long priceId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM standard_price WHERE id = ?",
                String.class,
                priceId
        );
    }

    private Long priceVersion(Long priceId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM standard_price WHERE id = ?",
                Long.class,
                priceId
        );
    }

    private String revokeBody(Long version) {
        return """
                {
                  "reason": "單價登錄錯誤",
                  "version": %d
                }
                """.formatted(version);
    }

    //已啟用的公司料號，基準單位 PCS，另設 BOX = 10；直接透過 entity 建立
    private CompanyItem createActiveItem() {
        CompanyItem item = companyItemRepository.saveAndFlush(new CompanyItem(
                ITEM_CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                "整合測試料號",
                UnitOfMeasure.PCS,
                null,
                1L
        ));
        item.submit();
        item.approve();
        item = companyItemRepository.saveAndFlush(item);

        itemUnitConversionRepository.saveAndFlush(
                new ItemUnitConversion(item, UnitOfMeasure.BOX, new BigDecimal("10"))
        );

        return item;
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
