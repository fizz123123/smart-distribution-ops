package com.smartdistribution.inventory;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.inventory.entity.InventoryReservation;
import com.smartdistribution.inventory.entity.InventoryReservationEvent;
import com.smartdistribution.inventory.entity.ReservationEventType;
import com.smartdistribution.inventory.entity.ReservationSourceType;
import com.smartdistribution.inventory.repository.InventoryReservationEventRepository;
import com.smartdistribution.inventory.repository.InventoryReservationRepository;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.InventoryPolicy;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.InventoryPolicyRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryAdjustmentApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@inventory-adjustment.integration.test";
    private static final String ITEM_CODE_PREFIX = "INVTEST-";
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
    private CompanyItemRepository companyItemRepository;

    @Autowired
    private InventoryPolicyRepository inventoryPolicyRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private InventoryReservationEventRepository inventoryReservationEventRepository;

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
                    DELETE FROM inventory_reservation_event
                    WHERE reservation_id IN (SELECT id FROM inventory_reservation WHERE company_item_id = ?)
                    """,
                    itemId
            );
            jdbcTemplate.update("DELETE FROM inventory_reservation WHERE company_item_id = ?", itemId);
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

    @Test
    void openingBalanceCreatesAdjustmentTransactionAndBalance() throws Exception {
        String warehouseManager = login(createUser("opening", "WAREHOUSE_MANAGER"));
        CompanyItem item = createItem(UnitOfMeasure.STICK);

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("OPENING_BALANCE", line(item.getId(), "100"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adjustmentNumber").value(containsString("ADJ-")))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.lines[0].quantityChangeBase").value(100));

        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header("Authorization", bearer(warehouseManager))
                        .param("keyword", item.getItemCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].onHandQty").value(100))
                .andExpect(jsonPath("$.content[0].reservedQty").value(0))
                .andExpect(jsonPath("$.content[0].availableQty").value(100));

        mockMvc.perform(get("/api/v1/inventory/items/{id}/transactions", item.getId())
                        .header("Authorization", bearer(warehouseManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("OPENING_BALANCE"))
                .andExpect(jsonPath("$.content[0].quantityChange").value(100));

        assertConsistent(warehouseManager, item);
    }

    @Test
    void openingBalanceIsAllowedOnceAndOnlyBeforeAnyActivity() throws Exception {
        String warehouseManager = login(createUser("opening-once", "WAREHOUSE_MANAGER"));
        CompanyItem item = createItem(UnitOfMeasure.STICK);

        postAdjustment(warehouseManager, adjustmentBody("OPENING_BALANCE", line(item.getId(), "10")));

        // 已有異動
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("OPENING_BALANCE", line(item.getId(), "5"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_OPENING_BALANCE_NOT_ALLOWED"));

        // 已有預留
        CompanyItem reservedItem = createItem(UnitOfMeasure.STICK);
        createReservation(reservedItem, "0");

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("OPENING_BALANCE", line(reservedItem.getId(), "5"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_OPENING_BALANCE_NOT_ALLOWED"));

        // 期初只能是正數
        CompanyItem newItem = createItem(UnitOfMeasure.STICK);

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("OPENING_BALANCE", line(newItem.getId(), "-5"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_OPENING_BALANCE_NOT_ALLOWED"));
    }

    @Test
    void decreaseCannotBreachReservationAndLeavesNoPartialUpdate() throws Exception {
        String warehouseManager = login(createUser("breach", "WAREHOUSE_MANAGER"));
        CompanyItem reservedItem = createItem(UnitOfMeasure.STICK);
        CompanyItem otherItem = createItem(UnitOfMeasure.STICK);

        postAdjustment(warehouseManager, adjustmentBody(
                "OPENING_BALANCE",
                line(reservedItem.getId(), "10") + ", " + line(otherItem.getId(), "10")
        ));
        String salesOrderNumber = createReservation(reservedItem, "8");

        // 可用量只剩 2，調減 3 會侵蝕預留；同一張調整裡另一個料號的調增也不能寫入
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody(
                                "COUNT_DIFFERENCE",
                                line(otherItem.getId(), "5") + ", " + line(reservedItem.getId(), "-3")
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_RESERVATION_WOULD_BE_BREACHED"))
                .andExpect(jsonPath("$.message").value(containsString(salesOrderNumber)));

        assertThat(onHandQty(reservedItem)).isEqualByComparingTo("10");
        assertThat(onHandQty(otherItem)).isEqualByComparingTo("10");
        assertThat(countTransactions(otherItem)).isEqualTo(1);

        // 調減在可用量以內可以過帳
        postAdjustment(warehouseManager, adjustmentBody("COUNT_DIFFERENCE", line(reservedItem.getId(), "-2")));
        assertThat(onHandQty(reservedItem)).isEqualByComparingTo("8");

        // 沒有預留的料號，調整後也不能小於 0
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("DAMAGE", line(otherItem.getId(), "-11"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_INSUFFICIENT"));

        assertConsistent(warehouseManager, reservedItem);
        assertConsistent(warehouseManager, otherItem);
    }

    @Test
    void reversalCreatesOppositeAdjustmentOnlyOnce() throws Exception {
        String warehouseManager = login(createUser("reverse", "WAREHOUSE_MANAGER"));
        CompanyItem item = createItem(UnitOfMeasure.STICK);

        postAdjustment(warehouseManager, adjustmentBody("OPENING_BALANCE", line(item.getId(), "10")));
        MvcResult adjustment = postAdjustment(
                warehouseManager,
                adjustmentBody("COUNT_DIFFERENCE", line(item.getId(), "5"))
        );
        Long adjustmentId = readLong(adjustment, "$.id");

        MvcResult reversal = mockMvc.perform(post("/api/v1/inventory/adjustments/{id}/reverse", adjustmentId)
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody(readLong(adjustment, "$.version"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalOfId").value(adjustmentId))
                .andExpect(jsonPath("$.lines[0].quantityChangeBase").value(-5))
                .andReturn();

        assertThat(onHandQty(item)).isEqualByComparingTo("10");

        MvcResult original = mockMvc.perform(get("/api/v1/inventory/adjustments/{id}", adjustmentId)
                        .header("Authorization", bearer(warehouseManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andReturn();

        // 已更正過的調整不能再更正
        mockMvc.perform(post("/api/v1/inventory/adjustments/{id}/reverse", adjustmentId)
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody(readLong(original, "$.version"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_ADJUSTMENT_NOT_REVERSIBLE"));

        // 更正紀錄本身也不能再被更正
        mockMvc.perform(post("/api/v1/inventory/adjustments/{id}/reverse", readLong(reversal, "$.id"))
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody(readLong(reversal, "$.version"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_ADJUSTMENT_NOT_REVERSIBLE"));

        assertConsistent(warehouseManager, item);
    }

    @Test
    void invalidLinesAreRejected() throws Exception {
        String warehouseManager = login(createUser("invalid", "WAREHOUSE_MANAGER"));
        CompanyItem item = createItem(UnitOfMeasure.STICK);

        // 「支」不允許小數
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("OPENING_BALANCE", line(item.getId(), "1.5"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVENTORY_ADJUSTMENT_LINES_INVALID"));

        // 同一料號重複
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody(
                                "COUNT_DIFFERENCE",
                                line(item.getId(), "1") + ", " + line(item.getId(), "2")
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVENTORY_ADJUSTMENT_LINES_INVALID"));
    }

    @Test
    void onlyWarehouseManagerCanPostAdjustments() throws Exception {
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        CompanyItem item = createItem(UnitOfMeasure.STICK);

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouse))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentBody("OPENING_BALANCE", line(item.getId(), "10"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 倉管可以查看庫存與安全庫存
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header("Authorization", bearer(warehouse))
                        .param("keyword", item.getItemCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].onHandQty").value(0));

        mockMvc.perform(get("/api/v1/company-items/{id}/inventory-policy", item.getId())
                        .header("Authorization", bearer(warehouse)))
                .andExpect(status().isOk());
    }

    private void assertConsistent(String token, CompanyItem item) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/inventory/consistency-check")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        // 開發資料庫可能有其他資料，只檢查本測試的料號
        List<Object> issues = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.issues[?(@.companyItemId == " + item.getId() + ")]"
        );
        assertThat(issues).isEmpty();
    }

    // 直接建立料號、安全庫存與庫存餘額，不經過料號的建立 API 與核准流程
    private CompanyItem createItem(UnitOfMeasure baseUnit) {
        CompanyItem item = companyItemRepository.saveAndFlush(new CompanyItem(
                ITEM_CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                "整合測試庫存料號",
                baseUnit,
                null,
                1L
        ));
        inventoryPolicyRepository.saveAndFlush(new InventoryPolicy(item));
        inventoryService.initializeForCompanyItem(item.getId());

        return item;
    }

    // SO 核准在後續 commit 才實作，這裡直接建立預留與事件，回傳 SO 編號
    private String createReservation(CompanyItem item, String quantity) {
        long fakeId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        String salesOrderNumber = "SO-T" + (fakeId % 1_000_000);

        InventoryReservation reservation = new InventoryReservation(
                fakeId,
                fakeId,
                salesOrderNumber,
                item.getId()
        );
        BigDecimal reservedQty = new BigDecimal(quantity);
        reservation.increase(reservedQty);
        inventoryReservationRepository.saveAndFlush(reservation);

        if (reservedQty.signum() > 0) {
            inventoryReservationEventRepository.saveAndFlush(new InventoryReservationEvent(
                    reservation,
                    ReservationEventType.RESERVE,
                    reservedQty,
                    ReservationSourceType.SO_APPROVAL,
                    fakeId,
                    1L,
                    Instant.now()
            ));
        }

        return salesOrderNumber;
    }

    private MvcResult postAdjustment(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private BigDecimal onHandQty(CompanyItem item) {
        return jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM inventory_balance WHERE company_item_id = ?",
                BigDecimal.class,
                item.getId()
        );
    }

    private int countTransactions(CompanyItem item) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE company_item_id = ?",
                Integer.class,
                item.getId()
        );

        return count == null ? 0 : count;
    }

    private String adjustmentBody(String reasonType, String lines) {
        return """
                {
                  "reasonType": "%s",
                  "description": "整合測試調整",
                  "lines": [%s]
                }
                """.formatted(reasonType, lines);
    }

    private String line(Long companyItemId, String quantity) {
        return """
                {"companyItemId": %d, "quantityChangeBase": %s}
                """.formatted(companyItemId, quantity);
    }

    private String reverseBody(Long version) {
        return """
                {"reason": "數量登錄錯誤", "version": %d}
                """.formatted(version);
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
}
