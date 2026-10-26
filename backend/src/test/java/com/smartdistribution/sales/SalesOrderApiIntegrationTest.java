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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SO 草稿、送審與快照（AT-011、AT-012、AT-013、AT-014、AT-021）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderApiIntegrationTest extends SalesOrderTestSupport {

    @Test
    void draftWithoutActivePriceCanBeSavedButNotSubmitted() throws Exception {
        String sales = login(createUser("no-price", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));

        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soNumber").value(startsWith("SO-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").doesNotExist())
                .andExpect(jsonPath("$.warnings[0]").value(containsString("無法送審")));

        SalesOrderVersion draft = createDraft(sales, draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS)));

        submit(sales, draft)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_ACTIVE_PRICE_REQUIRED"))
                .andExpect(jsonPath("$.message").value(containsString("第 1 行")));
    }

    @Test
    void submissionSnapshotsPaymentTermConversionPriceAndAmounts() throws Exception {
        String sales = login(createUser("snapshot", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.NET_30);

        CompanyItem pe = createActiveItem(UnitOfMeasure.STICK);
        addConversion(pe, UnitOfMeasure.BOX, "10");
        CompanyItem pu = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping peMapping = createActiveMapping(customer, pe);
        CustomerItemMapping puMapping = createActiveMapping(customer, pu);
        createActivePrice(peMapping, UnitOfMeasure.STICK, "100");
        createActivePrice(peMapping, UnitOfMeasure.BOX, "950");
        CustomerPrice puPrice = createActivePrice(puMapping, UnitOfMeasure.PCS, "200");

        // SO_MAIN：PE 10 支、PU 5 個 → 未稅 2,000、稅額 100、含稅 2,100；另加 PE 2 箱 → 基準數量 20 支
        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer,
                line(peMapping, "10", UnitOfMeasure.STICK) + ", "
                        + line(puMapping, "5", UnitOfMeasure.PCS) + ", "
                        + line(peMapping, "2", UnitOfMeasure.BOX)));

        getOrder(sales, salesOrderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.paymentTerm").value("NET_30"))
                .andExpect(jsonPath("$.untaxedAmount").value(3900))
                .andExpect(jsonPath("$.taxAmount").value(195))
                .andExpect(jsonPath("$.totalAmount").value(4095))
                .andExpect(jsonPath("$.lines[1].unitPriceExcludingTax").value(200))
                .andExpect(jsonPath("$.lines[1].lineAmountExcludingTax").value(1000))
                .andExpect(jsonPath("$.lines[2].conversionFactor").value(10))
                .andExpect(jsonPath("$.lines[2].baseQty").value(20))
                .andExpect(jsonPath("$.lines[2].lineAmountExcludingTax").value(1900));

        // 送審後修改付款條件、啟用新版價格，既有 SO 的快照都不變（AT-012、AT-013）
        customer.changePaymentTerm(PaymentTerm.DUE_0);
        customerRepository.saveAndFlush(customer);
        puPrice.replace();
        customerPriceRepository.saveAndFlush(puPrice);
        createActivePrice(puMapping, UnitOfMeasure.PCS, "250");

        getOrder(sales, salesOrderId)
                .andExpect(jsonPath("$.paymentTerm").value("NET_30"))
                .andExpect(jsonPath("$.lines[1].unitPriceExcludingTax").value(200))
                .andExpect(jsonPath("$.totalAmount").value(4095));
    }

    @Test
    void deliveryInfoAndLinesAreRequiredToSubmit() throws Exception {
        String sales = login(createUser("required", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);

        SalesOrderVersion empty = createDraft(sales, draftBody(customer, ""));
        submit(sales, empty)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_LINES_REQUIRED"));

        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        // 外送卻沒有地址與聯絡人
        SalesOrderVersion noAddress = createDraft(sales, """
                {
                  "customerId": %d,
                  "requestedDeliveryDate": "2026-11-20",
                  "deliveryMethod": "DELIVERY",
                  "lines": [%s]
                }
                """.formatted(customer.getId(), line(mapping, "1", UnitOfMeasure.PCS)));
        submit(sales, noAddress)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_DELIVERY_INFO_INCOMPLETE"));

        // 客戶自取不需要地址與聯絡人
        SalesOrderVersion pickup = createDraft(sales, """
                {
                  "customerId": %d,
                  "requestedDeliveryDate": "2026-11-20",
                  "deliveryMethod": "CUSTOMER_PICKUP",
                  "lines": [%s]
                }
                """.formatted(customer.getId(), line(mapping, "1", UnitOfMeasure.PCS)));
        submit(sales, pickup).andExpect(status().isOk());
    }

    @Test
    void draftLinesMustBelongToCustomerAndFollowUnitRules() throws Exception {
        String sales = login(createUser("lines", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        Customer otherCustomer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem item = createActiveItem(UnitOfMeasure.STICK);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        CustomerItemMapping otherMapping = createActiveMapping(otherCustomer, item);

        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, line(otherMapping, "1", UnitOfMeasure.STICK))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_MAPPING_CUSTOMER_MISMATCH"));

        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer,
                                line(mapping, "1", UnitOfMeasure.STICK) + ", " + line(mapping, "2", UnitOfMeasure.STICK))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_LINE_INVALID"));

        // 「支」不允許小數
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, line(mapping, "1.5", UnitOfMeasure.STICK))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_LINE_INVALID"));

        // 沒有設定換算的單位可以存草稿，送審時才擋下
        createActivePrice(mapping, UnitOfMeasure.BOX, "950");
        SalesOrderVersion draft = createDraft(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.BOX)));
        submit(sales, draft)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_UNIT_INVALID"));
    }

    @Test
    void pendingOrderIsLockedAndDraftCanBeCancelled() throws Exception {
        String sales = login(createUser("lock", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        // 草稿整份修改：數量改為 3，版本加 1
        SalesOrderVersion draft = createDraft(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        mockMvc.perform(patch("/api/v1/sales-orders/{id}", draft.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer, line(mapping, "3", UnitOfMeasure.PCS), draft.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.version").value(draft.version() + 1));

        // 用舊版本修改會衝突
        mockMvc.perform(patch("/api/v1/sales-orders/{id}", draft.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer, line(mapping, "4", UnitOfMeasure.PCS), draft.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 待核准期間不能修改，也不能放棄
        Long pendingId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        Long pendingVersion = readLong(getOrder(sales, pendingId).andReturn(), "$.version");

        mockMvc.perform(patch("/api/v1/sales-orders/{id}", pendingId)
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer, line(mapping, "2", UnitOfMeasure.PCS), pendingVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_STATUS_CONFLICT"));

        mockMvc.perform(post("/api/v1/sales-orders/{id}/cancel", pendingId)
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d}
                                """.formatted(pendingVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_STATUS_CONFLICT"));

        // 草稿可以放棄
        SalesOrderVersion abandoned = createDraft(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        mockMvc.perform(post("/api/v1/sales-orders/{id}/cancel", abandoned.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d}
                                """.formatted(abandoned.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void responsibleSalesMustBeActiveSalesUser() throws Exception {
        String salesManager = login(createUser("on-behalf", "SALES_MANAGER"));
        Long salesUserId = createUser("owner", "SALES").getId();
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);

        // 主管代建且沒有業務角色時，必須指定負責業務
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_RESPONSIBLE_SALES_INVALID"));

        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": %d, "responsibleSalesUserId": %d, "lines": []}
                                """.formatted(customer.getId(), salesUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsibleSales.id").value(salesUserId));
    }

    @Test
    void inactiveCustomerCannotBeUsedAndOnlyPermittedRolesCanCreate() throws Exception {
        String sales = login(createUser("inactive", "SALES"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);

        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(warehouse))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, "")))
                .andExpect(status().isForbidden());

        customer.deactivate();
        customerRepository.saveAndFlush(customer);

        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_CUSTOMER_INACTIVE"));
    }

    private String updateBody(Customer customer, String lines, Long version) {
        return """
                {
                  "customerId": %d,
                  "requestedDeliveryDate": "2026-11-20",
                  "deliveryMethod": "CUSTOMER_PICKUP",
                  "lines": [%s],
                  "version": %d
                }
                """.formatted(customer.getId(), lines, version);
    }
}
