package com.smartdistribution.sales;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.InventoryPolicy;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.InventoryPolicyRepository;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customer.repository.CustomerRepository;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customeritemmapping.repository.CustomerItemMappingRepository;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import com.smartdistribution.masterdata.customerprice.repository.CustomerPriceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SO 整合測試共用的資料準備與清理。
 * 主檔直接透過 entity 建立並轉為啟用，不經過各自的核准流程，讓測試只專注在 SO 與預留。
 * 庫存一律透過期初庫存 API 建立，異動與餘額才會一致。
 */
abstract class SalesOrderTestSupport {

    static final String TEST_EMAIL_SUFFIX = "@sales-order.integration.test";
    static final String CUSTOMER_NAME_PREFIX = "整合測試客戶-SO-";
    static final String ITEM_CODE_PREFIX = "SOTEST-";
    static final String PASSWORD = "Test1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    CompanyItemRepository companyItemRepository;

    @Autowired
    InventoryPolicyRepository inventoryPolicyRepository;

    @Autowired
    ItemUnitConversionRepository itemUnitConversionRepository;

    @Autowired
    CustomerItemMappingRepository customerItemMappingRepository;

    @Autowired
    CustomerPriceRepository customerPriceRepository;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        List<Long> salesOrderIds = jdbcTemplate.queryForList(
                """
                SELECT so.id FROM sales_order so
                JOIN customer customer ON customer.id = so.customer_id
                WHERE customer.name LIKE ?
                """,
                Long.class,
                CUSTOMER_NAME_PREFIX + "%"
        );

        for (Long salesOrderId : salesOrderIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'SALES_ORDER' AND target_id = ?
                    )
                    """,
                    salesOrderId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'SALES_ORDER' AND target_id = ?",
                    salesOrderId
            );

            List<Long> cancellationIds = jdbcTemplate.queryForList(
                    "SELECT id FROM sales_order_cancellation WHERE sales_order_id = ?",
                    Long.class,
                    salesOrderId
            );
            for (Long cancellationId : cancellationIds) {
                jdbcTemplate.update(
                        """
                        DELETE FROM approval_action
                        WHERE approval_request_id IN (
                            SELECT id FROM approval_request
                            WHERE target_type = 'SO_CANCELLATION' AND target_id = ?
                        )
                        """,
                        cancellationId
                );
                jdbcTemplate.update(
                        "DELETE FROM approval_request WHERE target_type = 'SO_CANCELLATION' AND target_id = ?",
                        cancellationId
                );
                jdbcTemplate.update(
                        "DELETE FROM sales_order_cancellation_line WHERE cancellation_id = ?",
                        cancellationId
                );
                jdbcTemplate.update("DELETE FROM sales_order_cancellation WHERE id = ?", cancellationId);
            }

            jdbcTemplate.update(
                    """
                    DELETE FROM inventory_reservation_event
                    WHERE reservation_id IN (SELECT id FROM inventory_reservation WHERE sales_order_id = ?)
                    """,
                    salesOrderId
            );
            jdbcTemplate.update("DELETE FROM inventory_reservation WHERE sales_order_id = ?", salesOrderId);
            jdbcTemplate.update("DELETE FROM sales_order_line WHERE sales_order_id = ?", salesOrderId);
            jdbcTemplate.update("DELETE FROM sales_order WHERE id = ?", salesOrderId);
        }

        List<Long> itemIds = jdbcTemplate.queryForList(
                "SELECT id FROM company_item WHERE item_code LIKE ?",
                Long.class,
                ITEM_CODE_PREFIX + "%"
        );

        for (Long itemId : itemIds) {
            jdbcTemplate.update("DELETE FROM inventory_transaction WHERE company_item_id = ?", itemId);
            List<Long> adjustmentIds = jdbcTemplate.queryForList(
                    "SELECT adjustment_id FROM inventory_adjustment_line WHERE company_item_id = ?",
                    Long.class,
                    itemId
            );
            for (Long adjustmentId : adjustmentIds) {
                jdbcTemplate.update("DELETE FROM inventory_adjustment_line WHERE adjustment_id = ?", adjustmentId);
                jdbcTemplate.update("DELETE FROM inventory_adjustment WHERE id = ?", adjustmentId);
            }
        }

        jdbcTemplate.update(
                """
                DELETE price FROM customer_price price
                JOIN customer_item_mapping mapping ON mapping.id = price.customer_item_mapping_id
                JOIN customer customer ON customer.id = mapping.customer_id
                WHERE customer.name LIKE ?
                """,
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE mapping FROM customer_item_mapping mapping
                JOIN customer customer ON customer.id = mapping.customer_id
                WHERE customer.name LIKE ?
                """,
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update("DELETE FROM customer WHERE name LIKE ?", CUSTOMER_NAME_PREFIX + "%");

        for (Long itemId : itemIds) {
            jdbcTemplate.update("DELETE FROM item_unit_conversion WHERE company_item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM inventory_policy WHERE company_item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM inventory_balance WHERE company_item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM company_item WHERE id = ?", itemId);
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

    // ===== 主檔 =====

    Customer createActiveCustomer(PaymentTerm paymentTerm) {
        Customer customer = customerRepository.saveAndFlush(new Customer(
                CUSTOMER_NAME_PREFIX + UUID.randomUUID().toString().substring(0, 8),
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
        customer.changePaymentTerm(paymentTerm);

        return customerRepository.saveAndFlush(customer);
    }

    CompanyItem createActiveItem(UnitOfMeasure baseUnit) {
        CompanyItem item = companyItemRepository.saveAndFlush(new CompanyItem(
                ITEM_CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                "整合測試銷售料號",
                baseUnit,
                null,
                1L
        ));
        item.submit();
        item.approve();
        // saveAndFlush 回傳的是合併後的物件，版本號才是最新的
        item = companyItemRepository.saveAndFlush(item);

        inventoryPolicyRepository.saveAndFlush(new InventoryPolicy(item));
        inventoryService.initializeForCompanyItem(item.getId());

        return item;
    }

    void addConversion(CompanyItem item, UnitOfMeasure unit, String factor) {
        itemUnitConversionRepository.saveAndFlush(new ItemUnitConversion(item, unit, new BigDecimal(factor)));
    }

    CustomerItemMapping createActiveMapping(Customer customer, CompanyItem item) {
        CustomerItemMapping mapping = customerItemMappingRepository.saveAndFlush(
                new CustomerItemMapping(customer, null, item, 1L)
        );
        mapping.submit();
        mapping.approve();

        return customerItemMappingRepository.saveAndFlush(mapping);
    }

    CustomerPrice createActivePrice(CustomerItemMapping mapping, UnitOfMeasure unit, String unitPrice) {
        CustomerPrice price = new CustomerPrice(mapping, unit, new BigDecimal(unitPrice), 1L);
        price.approve(1L, Instant.now());

        return customerPriceRepository.saveAndFlush(price);
    }

    // 期初庫存透過 API 建立，異動與餘額才會一致
    void postOpeningBalance(String warehouseManagerToken, CompanyItem item, String quantity) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManagerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonType": "OPENING_BALANCE",
                                  "description": "整合測試期初",
                                  "lines": [{"companyItemId": %d, "quantityChangeBase": %s}]
                                }
                                """.formatted(item.getId(), quantity)))
                .andExpect(status().isCreated());
    }

    // ===== SO =====

    String line(CustomerItemMapping mapping, String quantity, UnitOfMeasure unit) {
        return """
                {"customerItemMappingId": %d, "quantity": %s, "unit": "%s"}
                """.formatted(mapping.getId(), quantity, unit.name());
    }

    String draftBody(Customer customer, String lines) {
        return """
                {
                  "customerId": %d,
                  "customerOrderNumber": "CUS-PO-1008",
                  "requestedDeliveryDate": "2026-11-20",
                  "deliveryMethod": "DELIVERY",
                  "deliveryAddress": "台中市西屯區工業區一路 1 號",
                  "contactName": "王先生",
                  "contactPhone": "0912000000",
                  "lines": [%s]
                }
                """.formatted(customer.getId(), lines);
    }

    SalesOrderVersion createDraft(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return new SalesOrderVersion(readLong(result, "$.id"), readLong(result, "$.version"));
    }

    ResultActions submit(String token, SalesOrderVersion salesOrder) throws Exception {
        return mockMvc.perform(post("/api/v1/sales-orders/{id}/submit", salesOrder.id())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d}
                        """.formatted(salesOrder.version())));
    }

    // 建立草稿並送審，回傳 SO ID
    Long createSubmittedOrder(String token, String body) throws Exception {
        SalesOrderVersion draft = createDraft(token, body);
        submit(token, draft).andExpect(status().isOk());

        return draft.id();
    }

    Long findPendingApprovalId(Long salesOrderId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'SALES_ORDER' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                salesOrderId
        );
    }

    ResultActions approve(String token, Long salesOrderId) throws Exception {
        return mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", findPendingApprovalId(salesOrderId))
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    ResultActions getOrder(String token, Long salesOrderId) throws Exception {
        return mockMvc.perform(get("/api/v1/sales-orders/{id}", salesOrderId)
                .header("Authorization", bearer(token)));
    }

    // ===== 餘量取消 =====

    String cancellationLine(Long salesOrderLineId, String quantity) {
        return """
                {"salesOrderLineId": %d, "quantity": %s}
                """.formatted(salesOrderLineId, quantity);
    }

    ResultActions createCancellation(String token, Long salesOrderId, String lines) throws Exception {
        return mockMvc.perform(post("/api/v1/sales-orders/{id}/cancellations", salesOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "客戶縮減需求", "lines": [%s]}
                        """.formatted(lines)));
    }

    // 建立取消申請並送審，回傳取消申請 ID
    Long createSubmittedCancellation(String token, Long salesOrderId, String lines) throws Exception {
        MvcResult result = createCancellation(token, salesOrderId, lines)
                .andExpect(status().isCreated())
                .andReturn();
        Long cancellationId = readLong(result, "$.id");

        mockMvc.perform(post("/api/v1/sales-order-cancellations/{id}/submit", cancellationId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d}
                                """.formatted(readLong(result, "$.version"))))
                .andExpect(status().isOk());

        return cancellationId;
    }

    ResultActions approveCancellation(String token, Long cancellationId) throws Exception {
        Long approvalRequestId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'SO_CANCELLATION' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                cancellationId
        );

        return mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", approvalRequestId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    // SO 明細 ID，依行號排序
    List<Long> findLineIds(Long salesOrderId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM sales_order_line WHERE sales_order_id = ? ORDER BY line_no",
                Long.class,
                salesOrderId
        );
    }

    // ===== 庫存檢查 =====

    BigDecimal onHandQty(CompanyItem item) {
        return jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM inventory_balance WHERE company_item_id = ?",
                BigDecimal.class,
                item.getId()
        );
    }

    BigDecimal reservedQty(CompanyItem item) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(open_qty), 0) FROM inventory_reservation WHERE company_item_id = ?",
                BigDecimal.class,
                item.getId()
        );
    }

    // 一致性檢查（AT-026）：只看本測試建立的料號，開發資料庫可能有其他資料
    void assertConsistent(String token, CompanyItem... items) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/inventory/consistency-check")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        for (CompanyItem item : items) {
            List<Object> issues = JsonPath.read(
                    result.getResponse().getContentAsString(),
                    "$.issues[?(@.companyItemId == " + item.getId() + ")]"
            );
            assertThat(issues).isEmpty();
        }
    }

    // ===== 帳號 =====

    AppUser createUser(String prefix, String... roleCodes) {
        AppUser user = userRepository.save(new AppUser(
                prefix + "-" + UUID.randomUUID() + TEST_EMAIL_SUFFIX,
                prefix,
                passwordEncoder.encode(PASSWORD)
        ));
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByRoleCode(roleCode).orElseThrow();
            userRoleRepository.save(new UserRole(user, role));
        }

        return user;
    }

    String login(AppUser user) throws Exception {
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

    Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    String bearer(String token) {
        return "Bearer " + token;
    }

    record SalesOrderVersion(Long id, Long version) {
    }
}
