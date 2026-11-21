package com.smartdistribution.sales;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.pricing.entity.CustomerPrice;
import com.smartdistribution.sales.service.SalesOrderBillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SO 草稿、送審與快照（AT-011、AT-012、AT-013、AT-014、AT-021）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderApiIntegrationTest extends SalesOrderTestSupport {

    @Autowired
    private SalesOrderBillingService salesOrderBillingService;

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
                .andExpect(jsonPath("$.code").value("SO_PRICE_REQUIRED"))
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
        replacePrice(puPrice, "250", LocalDate.now(TAIPEI));

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
        mockMvc.perform(put("/api/v1/sales-orders/{id}", draft.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer, line(mapping, "3", UnitOfMeasure.PCS), draft.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.version").value(draft.version() + 1));

        // 用舊版本修改會衝突
        mockMvc.perform(put("/api/v1/sales-orders/{id}", draft.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer, line(mapping, "4", UnitOfMeasure.PCS), draft.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 待核准期間不能修改，也不能放棄
        Long pendingId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        Long pendingVersion = readLong(getOrder(sales, pendingId).andReturn(), "$.version");

        mockMvc.perform(put("/api/v1/sales-orders/{id}", pendingId)
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
    void responsibleSalesMustHaveSoCreatePermission() throws Exception {
        AppUser salesManagerUser = createUser("manager", "SALES_MANAGER");
        String salesManager = login(salesManagerUser);
        Long salesUserId = createUser("owner", "SALES").getId();
        Long warehouseUserId = createUser("warehouse", "WAREHOUSE").getId();
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);

        //只有業務主管角色也能自己建 SO，並成為負責業務：主管包含業務的全部權限
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsibleSales.id").value(salesManagerUser.getId()));

        //可以指定其他業務為負責業務
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": %d, "responsibleSalesUserId": %d, "lines": []}
                                """.formatted(customer.getId(), salesUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsibleSales.id").value(salesUserId));

        //沒有建立 SO 權限的人不能當負責業務
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": %d, "responsibleSalesUserId": %d, "lines": []}
                                """.formatted(customer.getId(), warehouseUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_RESPONSIBLE_SALES_INVALID"));
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

    @Test
    void standardPriceIsUsedWhenNoCustomerPriceApplies() throws Exception {
        String sales = login(createUser("standard-price", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        LocalDate today = LocalDate.now(TAIPEI);
        createActiveStandardPrice(item, UnitOfMeasure.PCS, "300", LocalDate.of(2020, 1, 1));

        //沒有客戶價格：用標準售價
        Long standardOnlyId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        getOrder(sales, standardOnlyId)
                .andExpect(jsonPath("$.lines[0].priceSource").value("STANDARD_PRICE"))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(300));

        //客戶價格只到 3 天前：訂單日期在期間內用客戶價格，之後改用標準售價
        CustomerPrice customerPrice = createActivePrice(mapping, UnitOfMeasure.PCS, "280");
        jdbcTemplate.update(
                "UPDATE customer_price SET effective_to = ? WHERE id = ?",
                today.minusDays(3),
                customerPrice.getId()
        );

        Long withinContractId = createSubmittedOrder(
                sales,
                draftBody(customer, today.minusDays(5), line(mapping, "1", UnitOfMeasure.PCS))
        );
        getOrder(sales, withinContractId)
                .andExpect(jsonPath("$.lines[0].priceSource").value("CUSTOMER_PRICE"))
                .andExpect(jsonPath("$.lines[0].priceId").value(customerPrice.getId()))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(280));

        Long afterContractId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        getOrder(sales, afterContractId)
                .andExpect(jsonPath("$.lines[0].priceSource").value("STANDARD_PRICE"))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(300));
    }

    @Test
    void unitPriceCanBeRaisedButNotBelowListPrice() throws Exception {
        String sales = login(createUser("override-sales", "SALES"));
        String salesManager = login(createUser("override-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        //低於底價：草稿可以存並提示，送審被擋
        SalesOrderVersion below = createDraft(sales, draftBody(customer, line(mapping, "2", UnitOfMeasure.PCS, "180")));
        getOrder(sales, below.id())
                .andExpect(jsonPath("$.lines[0].listUnitPrice").value(200))
                .andExpect(jsonPath("$.warnings[0]").value(containsString("低於底價 200")));
        submit(sales, below)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_PRICE_BELOW_LIST_PRICE"))
                .andExpect(jsonPath("$.message").value(containsString("底價 200")));

        //留空：成交價就是底價
        Long listPriceId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "2", UnitOfMeasure.PCS)));
        getOrder(sales, listPriceId)
                .andExpect(jsonPath("$.lines[0].requestedUnitPrice").doesNotExist())
                .andExpect(jsonPath("$.lines[0].listUnitPrice").value(200))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(200));

        //往上調：金額與應收照成交價，主管核准時看得到差異
        Long raisedId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "2", UnitOfMeasure.PCS, "230")));
        getOrder(sales, raisedId)
                .andExpect(jsonPath("$.lines[0].listUnitPrice").value(200))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(230))
                .andExpect(jsonPath("$.lines[0].lineAmountExcludingTax").value(460));

        mockMvc.perform(get("/api/v1/approval-requests/{id}", findPendingApprovalId(raisedId))
                        .header("Authorization", bearer(salesManager)))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("成交價 230.00，底價 200.00"))));

        assertThat(salesOrderBillingService.findForBilling(raisedId).lines().get(0).unitPriceExcludingTax())
                .isEqualByComparingTo("230");
    }

    @Test
    void lineDeliveryDateOverridesHeaderAndAllowsSplitDeliveries() throws Exception {
        String sales = login(createUser("line-date-sales", "SALES"));
        String salesManager = login(createUser("line-date-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");
        LocalDate headerDate = LocalDate.of(2026, 11, 20);
        LocalDate laterDate = LocalDate.of(2026, 11, 25);

        //同料號同單位，實際交期相同被擋：一行明寫表頭的日期、一行沿用表頭
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer,
                                line(mapping, "1", UnitOfMeasure.PCS, null, headerDate) + ", "
                                        + line(mapping, "2", UnitOfMeasure.PCS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_LINE_INVALID"));

        //交期不同可以分兩行：第 1 行沿用表頭、第 2 行自己的交期
        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer,
                line(mapping, "1", UnitOfMeasure.PCS) + ", "
                        + line(mapping, "2", UnitOfMeasure.PCS, null, laterDate)));

        getOrder(sales, salesOrderId)
                .andExpect(jsonPath("$.lines[0].requestedDeliveryDate").doesNotExist())
                .andExpect(jsonPath("$.lines[0].deliveryDate").value(headerDate.toString()))
                .andExpect(jsonPath("$.lines[1].deliveryDate").value(laterDate.toString()));

        mockMvc.perform(get("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .param("customerId", customer.getId().toString()))
                .andExpect(jsonPath("$.content[0].earliestDeliveryDate").value(headerDate.toString()));

        //退回後改表頭交期：沿用表頭的那行跟著變，自己填的不變
        mockMvc.perform(post("/api/v1/approval-requests/{id}/return", findPendingApprovalId(salesOrderId))
                        .header("Authorization", bearer(salesManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "客戶改交期"}
                                """))
                .andExpect(status().isOk());

        Long version = readLong(getOrder(sales, salesOrderId).andReturn(), "$.version");
        mockMvc.perform(put("/api/v1/sales-orders/{id}", salesOrderId)
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "orderDate": "%s",
                                  "requestedDeliveryDate": "2026-11-22",
                                  "deliveryMethod": "CUSTOMER_PICKUP",
                                  "lines": [%s, %s],
                                  "version": %d
                                }
                                """.formatted(
                                customer.getId(),
                                LocalDate.now(TAIPEI),
                                line(mapping, "1", UnitOfMeasure.PCS),
                                line(mapping, "2", UnitOfMeasure.PCS, null, laterDate),
                                version
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].deliveryDate").value("2026-11-22"))
                .andExpect(jsonPath("$.lines[1].deliveryDate").value(laterDate.toString()));
    }

    @Test
    void everyLineNeedsDeliveryDateToSubmit() throws Exception {
        String sales = login(createUser("no-date-sales", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        //表頭沒有交期，第 1 行有自己的交期、第 2 行沒有
        SalesOrderVersion draft = createDraft(sales, """
                {
                  "customerId": %d,
                  "deliveryMethod": "CUSTOMER_PICKUP",
                  "lines": [%s, %s]
                }
                """.formatted(
                customer.getId(),
                line(mapping, "1", UnitOfMeasure.PCS, null, LocalDate.of(2026, 11, 25)),
                line(mapping, "2", UnitOfMeasure.PCS)
        ));

        submit(sales, draft)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_DELIVERY_INFO_INCOMPLETE"))
                .andExpect(jsonPath("$.message").value(containsString("第 2 行")));
    }

    @Test
    void orderDateDefaultsToTodayAndCannotBeInTheFuture() throws Exception {
        String sales = login(createUser("order-date", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        LocalDate today = LocalDate.now(TAIPEI);

        //沒填就是建立當天
        SalesOrderVersion draft = createDraft(sales, draftBody(customer, line(mapping, "1", UnitOfMeasure.PCS)));
        getOrder(sales, draft.id())
                .andExpect(jsonPath("$.orderDate").value(today.toString()));

        //往前補登可以
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, today.minusDays(7), line(mapping, "1", UnitOfMeasure.PCS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderDate").value(today.minusDays(7).toString()));

        //未來日期建立與修改都被擋
        mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody(customer, today.plusDays(1), line(mapping, "1", UnitOfMeasure.PCS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_ORDER_DATE_INVALID"));

        mockMvc.perform(put("/api/v1/sales-orders/{id}", draft.id())
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(customer, today.plusDays(1), line(mapping, "1", UnitOfMeasure.PCS), draft.version())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_ORDER_DATE_INVALID"));
    }

    private String updateBody(Customer customer, String lines, Long version) {
        return updateBody(customer, LocalDate.now(TAIPEI), lines, version);
    }

    private String updateBody(Customer customer, LocalDate orderDate, String lines, Long version) {
        return """
                {
                  "customerId": %d,
                  "orderDate": "%s",
                  "requestedDeliveryDate": "2026-11-20",
                  "deliveryMethod": "CUSTOMER_PICKUP",
                  "lines": [%s],
                  "version": %d
                }
                """.formatted(customer.getId(), orderDate, lines, version);
    }
}
