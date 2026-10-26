package com.smartdistribution.sales;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.customerprice.entity.CustomerPrice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SO 核准與預留（AT-017、AT-018、AT-019、AT-022、AT-024、AT-026）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderApprovalApiIntegrationTest extends SalesOrderTestSupport {

    @Test
    void approvalReservesAvailableStockWithoutChangingOnHand() throws Exception {
        String sales = login(createUser("main-sales", "SALES"));
        String salesManager = login(createUser("main-manager", "SALES_MANAGER"));
        String warehouseManager = login(createUser("main-warehouse", "WAREHOUSE_MANAGER"));

        Customer customer = createActiveCustomer(PaymentTerm.NET_30);
        CompanyItem pe = createActiveItem(UnitOfMeasure.STICK);
        CompanyItem pu = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping peMapping = createActiveMapping(customer, pe);
        CustomerItemMapping puMapping = createActiveMapping(customer, pu);
        createActivePrice(peMapping, UnitOfMeasure.STICK, "100");
        createActivePrice(puMapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, pe, "10");
        postOpeningBalance(warehouseManager, pu, "2");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer,
                line(peMapping, "10", UnitOfMeasure.STICK) + ", " + line(puMapping, "5", UnitOfMeasure.PCS)));

        // 核准前，主管看得到依目前可用量試算的缺料提示
        mockMvc.perform(get("/api/v1/approval-requests/{id}", findPendingApprovalId(salesOrderId))
                        .header("Authorization", bearer(salesManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasItem(containsString("尚缺 3"))));

        approve(salesManager, salesOrderId).andExpect(status().isOk());

        // SO_MAIN：預留 PE 10、PU 2，PU 尚待規劃 3；可出貨實體數量不因預留而扣減（AT-022）
        getOrder(sales, salesOrderId)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").exists())
                .andExpect(jsonPath("$.lines[0].supply.reservedQty").value(10))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(0))
                .andExpect(jsonPath("$.lines[1].supply.reservedQty").value(2))
                .andExpect(jsonPath("$.lines[1].supply.unplannedQty").value(3));

        assertThat(onHandQty(pe)).isEqualByComparingTo("10");
        assertThat(onHandQty(pu)).isEqualByComparingTo("2");

        // 預留明細可以追溯到 SO 與核准申請
        mockMvc.perform(get("/api/v1/inventory/items/{id}/reservations", pu.getId())
                        .header("Authorization", bearer(warehouseManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openQty").value(2))
                .andExpect(jsonPath("$[0].events[0].eventType").value("RESERVE"))
                .andExpect(jsonPath("$[0].events[0].sourceType").value("SO_APPROVAL"));

        // 有預留後，調減不得侵蝕預留（AT-024）
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(warehouseManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonType": "COUNT_DIFFERENCE",
                                  "description": "盤點短少",
                                  "lines": [{"companyItemId": %d, "quantityChangeBase": -1}]
                                }
                                """.formatted(pu.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_RESERVATION_WOULD_BE_BREACHED"));

        assertConsistent(warehouseManager, pe, pu);
    }

    @Test
    void replacedPriceDoesNotBlockApprovalAndKeepsSnapshot() throws Exception {
        String sales = login(createUser("replaced-sales", "SALES"));
        String salesManager = login(createUser("replaced-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        CustomerPrice price = createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));

        // 送審後正常換價：舊版 REPLACED、新版 ACTIVE（AT-017）
        price.replace();
        customerPriceRepository.saveAndFlush(price);
        createActivePrice(mapping, UnitOfMeasure.PCS, "250");

        mockMvc.perform(get("/api/v1/approval-requests/{id}", findPendingApprovalId(salesOrderId))
                        .header("Authorization", bearer(salesManager)))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("價格已更新"))));

        approve(salesManager, salesOrderId).andExpect(status().isOk());

        getOrder(sales, salesOrderId)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(200));
    }

    @Test
    void revokedPriceBlocksApprovalWithoutReservation() throws Exception {
        String sales = login(createUser("revoked-sales", "SALES"));
        String salesManager = login(createUser("revoked-manager", "SALES_MANAGER"));
        String warehouseManager = login(createUser("revoked-warehouse", "WAREHOUSE_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        CustomerPrice price = createActivePrice(mapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, item, "10");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "3", UnitOfMeasure.PCS)));

        price.revoke(1L, "單價登錄錯誤", Instant.now());
        customerPriceRepository.saveAndFlush(price);

        // 價格撤銷時拒絕核准，不建立預留，SO 維持待核准（AT-018）
        approve(salesManager, salesOrderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_PRICE_REVOKED"));

        getOrder(sales, salesOrderId).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(reservedQty(item)).isEqualByComparingTo("0");
        Integer reservationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_reservation WHERE sales_order_id = ?",
                Integer.class,
                salesOrderId
        );
        assertThat(reservationCount).isZero();

        // 系統不自動退回，由主管手動退回並留下意見
        mockMvc.perform(post("/api/v1/approval-requests/{id}/return", findPendingApprovalId(salesOrderId))
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "價格已撤銷，請依目前價格重新送審"}
                                """))
                .andExpect(status().isOk());

        getOrder(sales, salesOrderId).andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    void priceRevokedAfterApprovalDoesNotChangeSnapshot() throws Exception {
        String sales = login(createUser("after-sales", "SALES"));
        String salesManager = login(createUser("after-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        CustomerPrice price = createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());

        price.revoke(1L, "單價登錄錯誤", Instant.now());
        customerPriceRepository.saveAndFlush(price);

        // 已核准 SO 不回溯修改（AT-019）
        getOrder(sales, salesOrderId)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(200))
                .andExpect(jsonPath("$.totalAmount").value(210));
    }

    @Test
    void deactivatedItemBlocksApproval() throws Exception {
        String sales = login(createUser("inactive-sales", "SALES"));
        String salesManager = login(createUser("inactive-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));

        item.deactivate();
        companyItemRepository.saveAndFlush(item);

        approve(salesManager, salesOrderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_MASTER_DATA_INACTIVE"));

        getOrder(sales, salesOrderId).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void managerCanApproveButSalesCannot() throws Exception {
        String sales = login(createUser("role-sales", "SALES"));
        String otherSales = login(createUser("role-other-sales", "SALES"));
        String manager = login(createUser("role-manager", "MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));

        approve(otherSales, salesOrderId).andExpect(status().isForbidden());

        // MANAGER 可替代核准 SO（D-040）
        approve(manager, salesOrderId).andExpect(status().isOk());
        getOrder(sales, salesOrderId).andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
