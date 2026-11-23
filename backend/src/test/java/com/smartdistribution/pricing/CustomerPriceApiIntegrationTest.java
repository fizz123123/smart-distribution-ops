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
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.pricing.entity.StandardPrice;
import com.smartdistribution.pricing.repository.StandardPriceRepository;
import com.smartdistribution.pricing.service.PriceLookupService;
import com.smartdistribution.pricing.service.PriceLookupService.ApplicablePrice;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerPriceApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@price.integration.test";
    private static final String CUSTOMER_NAME_PREFIX = "整合測試客戶-價格-";
    private static final String ITEM_CODE_PREFIX = "PRCTEST-";
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
    private CustomerRepository customerRepository;

    @Autowired
    private CompanyItemRepository companyItemRepository;

    @Autowired
    private ItemUnitConversionRepository itemUnitConversionRepository;

    @Autowired
    private CustomerItemMappingRepository customerItemMappingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PriceLookupService priceLookupService;

    @Autowired
    private StandardPriceRepository standardPriceRepository;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> priceIds = jdbcTemplate.queryForList(
                """
                SELECT price.id
                FROM customer_price price
                JOIN customer_item_mapping mapping ON mapping.id = price.customer_item_mapping_id
                JOIN customer customer ON customer.id = mapping.customer_id
                WHERE customer.name LIKE ?
                """,
                Long.class,
                CUSTOMER_NAME_PREFIX + "%"
        );

        for (Long priceId : priceIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'CUSTOMER_PRICE' AND target_id = ?
                    )
                    """,
                    priceId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'CUSTOMER_PRICE' AND target_id = ?",
                    priceId
            );
            jdbcTemplate.update("DELETE FROM customer_price WHERE id = ?", priceId);
        }

        jdbcTemplate.update(
                """
                DELETE mapping FROM customer_item_mapping mapping
                JOIN customer customer ON customer.id = mapping.customer_id
                WHERE customer.name LIKE ?
                """,
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update("DELETE FROM customer WHERE name LIKE ?", CUSTOMER_NAME_PREFIX + "%");
        jdbcTemplate.update(
                """
                DELETE price FROM standard_price price
                JOIN company_item item ON item.id = price.company_item_id
                WHERE item.item_code LIKE ?
                """,
                ITEM_CODE_PREFIX + "%"
        );
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
    void createdPriceGoesStraightToReview() throws Exception {
        String sales = login(createUser("create", "SALES"));
        CustomerItemMapping mapping = createActiveMapping();

        Long priceId = createPrice(sales, mapping.getId(), "PCS", "125.00");

        assertThat(priceStatus(priceId)).isEqualTo("PENDING_REVIEW");
        assertThat(findPendingApprovalId(priceId)).isNotNull();
    }

    @Test
    void onlyOnePendingPricePerMappingAndUnit() throws Exception {
        String sales = login(createUser("pending", "SALES"));
        CustomerItemMapping mapping = createActiveMapping();

        createPrice(sales, mapping.getId(), "PCS", "125.00");

        performCreate(sales, mapping.getId(), "PCS", "130.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PRICE_PENDING_EXISTS"));

        // 不同計價單位可以各有一筆待覆核；BOX 是料號已設定換算的單位
        performCreate(sales, mapping.getId(), "BOX", "1200.00")
                .andExpect(status().isCreated());
    }

    @Test
    void pricingUnitMustBeBaseUnitOrConvertedUnit() throws Exception {
        String sales = login(createUser("unit", "SALES"));
        CustomerItemMapping mapping = createActiveMapping();

        performCreate(sales, mapping.getId(), "KG", "50.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PRICE_UNIT_INVALID"));
    }

    @Test
    void approvedPriceReplacesCurrentActivePrice() throws Exception {
        AppUser salesUser = createUser("replace-sales", "SALES");
        AppUser managerUser = createUser("replace-manager", "SALES_MANAGER");
        String sales = login(salesUser);
        String manager = login(managerUser);
        CustomerItemMapping mapping = createActiveMapping();

        Long firstPriceId = createPrice(sales, mapping.getId(), "PCS", "120.00");
        approve(manager, findPendingApprovalId(firstPriceId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customer-prices/{id}", firstPriceId)
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reviewedBy").value(managerUser.getId()));

        Long secondPriceId = createPrice(sales, mapping.getId(), "PCS", "125.00");
        Long secondApprovalId = findPendingApprovalId(secondPriceId);

        // 核准詳情以警示顯示將被取代的現行價格
        mockMvc.perform(get("/api/v1/approval-requests/{id}", secondApprovalId)
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value(containsString("120.00")));

        approve(manager, secondApprovalId).andExpect(status().isOk());

        assertThat(priceStatus(firstPriceId)).isEqualTo("REPLACED");
        assertThat(priceStatus(secondPriceId)).isEqualTo("ACTIVE");
    }

    @Test
    void returnedPriceIsRejected() throws Exception {
        String sales = login(createUser("reject-sales", "SALES"));
        String manager = login(createUser("reject-manager", "SALES_MANAGER"));
        CustomerItemMapping mapping = createActiveMapping();

        Long priceId = createPrice(sales, mapping.getId(), "PCS", "99.00");

        mockMvc.perform(post("/api/v1/approval-requests/{id}/return", findPendingApprovalId(priceId))
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "低於成本，請重新報價"}
                                """))
                .andExpect(status().isOk());

        assertThat(priceStatus(priceId)).isEqualTo("REJECTED");
    }

    @Test
    void onlySalesManagerCanRevokeActivePrice() throws Exception {
        String sales = login(createUser("revoke-sales", "SALES"));
        String manager = login(createUser("revoke-manager", "SALES_MANAGER"));
        CustomerItemMapping mapping = createActiveMapping();

        Long priceId = createPrice(sales, mapping.getId(), "PCS", "125.00");
        approve(manager, findPendingApprovalId(priceId)).andExpect(status().isOk());
        Long version = priceVersion(priceId);

        mockMvc.perform(post("/api/v1/customer-prices/{id}/revoke", priceId)
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeBody(version)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/customer-prices/{id}/revoke", priceId)
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeBody(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokeReason").value("單價登錄錯誤"));
    }

    @Test
    void scheduledPriceEndsCurrentPriceTheDayBefore() throws Exception {
        String sales = login(createUser("schedule-sales", "SALES"));
        String manager = login(createUser("schedule-manager", "SALES_MANAGER"));
        CustomerItemMapping mapping = createActiveMapping();
        LocalDate today = LocalDate.now(clock);

        Long currentId = createPrice(sales, mapping.getId(), "PCS", "120.00");
        approve(manager, findPendingApprovalId(currentId)).andExpect(status().isOk());

        //預先排定 10 天後調價
        MvcResult created = performCreate(sales, mapping.getId(), "PCS", "130.00", today.plusDays(10), null)
                .andExpect(status().isCreated())
                .andReturn();
        Long scheduledId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        Long approvalId = findPendingApprovalId(scheduledId);

        mockMvc.perform(get("/api/v1/approval-requests/{id}", approvalId)
                        .header("Authorization", bearer(manager)))
                .andExpect(jsonPath("$.warnings[0]").value(containsString("於 " + today.plusDays(9) + " 結束")));

        approve(manager, approvalId).andExpect(status().isOk());

        assertThat(priceStatus(currentId)).isEqualTo("REPLACED");
        assertThat(effectiveTo(currentId)).isEqualTo(today.plusDays(9));

        //生效日之前仍取用舊價格，生效日當天起取用新價格
        assertThat(applicablePriceId(mapping, today)).contains(currentId);
        assertThat(applicablePriceId(mapping, today.plusDays(9))).contains(currentId);
        assertThat(applicablePriceId(mapping, today.plusDays(10))).contains(scheduledId);

        //被取代但還沒到期的版本也能撤銷，空出的期間不會接回前一版
        mockMvc.perform(post("/api/v1/customer-prices/{id}/revoke", currentId)
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeBody(priceVersion(currentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        assertThat(applicablePriceId(mapping, today)).isEmpty();
    }

    @Test
    void invalidOrOverlappingPeriodIsRejected() throws Exception {
        String sales = login(createUser("period-sales", "SALES"));
        String manager = login(createUser("period-manager", "SALES_MANAGER"));
        CustomerItemMapping mapping = createActiveMapping();
        LocalDate today = LocalDate.now(clock);

        //不溯及既往、截止日不能早於生效日
        performCreate(sales, mapping.getId(), "PCS", "120.00", today.minusDays(1), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_INVALID"));
        performCreate(sales, mapping.getId(), "PCS", "120.00", today.plusDays(5), today.plusDays(4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_INVALID"));

        //已排定 10 天後的版本，新版本不能排在它前面
        MvcResult created = performCreate(sales, mapping.getId(), "PCS", "120.00", today.plusDays(10), null)
                .andExpect(status().isCreated())
                .andReturn();
        Long scheduledId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        approve(manager, findPendingApprovalId(scheduledId)).andExpect(status().isOk());

        performCreate(sales, mapping.getId(), "PCS", "110.00", today.plusDays(5), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_CONFLICT"));
    }

    @Test
    void expiredPriceIsNotApplied() throws Exception {
        String sales = login(createUser("expired-sales", "SALES"));
        String manager = login(createUser("expired-manager", "SALES_MANAGER"));
        CustomerItemMapping mapping = createActiveMapping();
        LocalDate today = LocalDate.now(clock);

        Long priceId = createPrice(sales, mapping.getId(), "PCS", "120.00");
        approve(manager, findPendingApprovalId(priceId)).andExpect(status().isOk());

        //模擬一筆一個月前生效、昨天到期的價格
        jdbcTemplate.update(
                "UPDATE customer_price SET effective_from = ?, effective_to = ? WHERE id = ?",
                today.minusDays(30),
                today.minusDays(1),
                priceId
        );

        mockMvc.perform(get("/api/v1/customer-prices/{id}", priceId)
                        .header("Authorization", bearer(sales)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expired").value(true));

        assertThat(applicablePriceId(mapping, today.minusDays(1))).contains(priceId);
        assertThat(applicablePriceId(mapping, today)).isEmpty();
    }

    @Test
    void approvalShowsDifferenceFromStandardPrice() throws Exception {
        String sales = login(createUser("compare-sales", "SALES"));
        String manager = login(createUser("compare-manager", "SALES_MANAGER"));
        CustomerItemMapping mapping = createActiveMapping();

        StandardPrice standardPrice = new StandardPrice(
                mapping.getCompanyItem(), UnitOfMeasure.PCS, new BigDecimal("130.00"), null, null, 1L
        );
        standardPrice.approve(1L, Instant.now(), LocalDate.now(clock));
        standardPriceRepository.saveAndFlush(standardPrice);

        Long priceId = createPrice(sales, mapping.getId(), "PCS", "125.00");

        mockMvc.perform(get("/api/v1/approval-requests/{id}", findPendingApprovalId(priceId))
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasItem(containsString("標準售價為 130.00 元／個，此客戶價格低 5.00 元"))));
    }

    @Test
    void mappingMustBeActive() throws Exception {
        String sales = login(createUser("inactive-mapping", "SALES"));
        CustomerItemMapping mapping = createActiveMapping();

        CustomerItemMapping storedMapping = customerItemMappingRepository.findById(mapping.getId()).orElseThrow();
        storedMapping.deactivate();
        customerItemMappingRepository.saveAndFlush(storedMapping);

        performCreate(sales, mapping.getId(), "PCS", "125.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PRICE_MAPPING_INACTIVE"));
    }

    private Long createPrice(String token, Long mappingId, String unit, String price) throws Exception {
        MvcResult result = performCreate(token, mappingId, unit, price)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private ResultActions performCreate(
            String token,
            Long mappingId,
            String unit,
            String price
    ) throws Exception {
        return performCreate(token, mappingId, unit, price, null, null);
    }

    private ResultActions performCreate(
            String token,
            Long mappingId,
            String unit,
            String price,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/customer-prices")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "customerItemMappingId": %d,
                          "pricingUnit": "%s",
                          "unitPriceExcludingTax": %s,
                          "effectiveFrom": %s,
                          "effectiveTo": %s
                        }
                        """.formatted(mappingId, unit, price, jsonDate(effectiveFrom), jsonDate(effectiveTo))));
    }

    private String jsonDate(LocalDate date) {
        return date == null ? "null" : "\"" + date + "\"";
    }

    private LocalDate effectiveTo(Long priceId) {
        return jdbcTemplate.queryForObject(
                "SELECT effective_to FROM customer_price WHERE id = ?",
                LocalDate.class,
                priceId
        );
    }

    private Optional<Long> applicablePriceId(CustomerItemMapping mapping, LocalDate date) {
        return priceLookupService.findApplicablePrice(mapping.getId(), UnitOfMeasure.PCS, date)
                .map(ApplicablePrice::priceId);
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
                WHERE target_type = 'CUSTOMER_PRICE' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                priceId
        );
    }

    private String priceStatus(Long priceId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM customer_price WHERE id = ?",
                String.class,
                priceId
        );
    }

    private Long priceVersion(Long priceId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM customer_price WHERE id = ?",
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

    // 已啟用的客戶、公司料號（基準單位 PCS，另設 BOX = 10）與對應，直接透過 entity 建立
    private CustomerItemMapping createActiveMapping() {
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
        customer = customerRepository.saveAndFlush(customer);

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

        CustomerItemMapping mapping = customerItemMappingRepository.saveAndFlush(
                new CustomerItemMapping(customer, null, item, 1L)
        );
        mapping.submit();
        mapping.approve();

        return customerItemMappingRepository.saveAndFlush(mapping);
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
