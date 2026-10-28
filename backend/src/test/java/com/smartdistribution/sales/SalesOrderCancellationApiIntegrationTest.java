package com.smartdistribution.sales;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SO 未出貨餘量取消（11 號第 5 節、Q5、Q9）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderCancellationApiIntegrationTest extends SalesOrderTestSupport {

    @Test
    void cancellationReleasesReservationOnlyBeyondRemainingDemand() throws Exception {
        String sales = login(createUser("release-sales", "SALES"));
        String salesManager = login(createUser("release-manager", "SALES_MANAGER"));
        String warehouseManager = login(createUser("release-warehouse", "WAREHOUSE_MANAGER"));

        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, item, "2");

        // 訂 5、庫存 2：核准後預留 2、尚待規劃 3
        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());
        Long lineId = findLineIds(salesOrderId).get(0);

        // 取消 2：剩餘需求 3 仍大於預留 2，先取消未預留的部分，不釋放預留
        Long first = createSubmittedCancellation(sales, salesOrderId, cancellationLine(lineId, "2"));
        approveCancellation(salesManager, first).andExpect(status().isOk());

        assertThat(reservedQty(item)).isEqualByComparingTo("2");
        getOrder(sales, salesOrderId)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.lines[0].supply.cancelledQty").value(2))
                .andExpect(jsonPath("$.lines[0].supply.reservedQty").value(2))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(1));

        // 再取消 2：剩餘需求 1，預留超出的 1 釋放回可用量
        Long second = createSubmittedCancellation(sales, salesOrderId, cancellationLine(lineId, "2"));
        approveCancellation(salesManager, second).andExpect(status().isOk());

        assertThat(reservedQty(item)).isEqualByComparingTo("1");
        assertThat(onHandQty(item)).isEqualByComparingTo("2");

        mockMvc.perform(get("/api/v1/sales-order-cancellations/{id}", second)
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").exists());

        mockMvc.perform(get("/api/v1/inventory/items/{id}/reservations", item.getId())
                        .header("Authorization", bearer(warehouseManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openQty").value(1))
                .andExpect(jsonPath("$[0].events[*].sourceType", hasItem("SO_CANCELLATION")));

        assertConsistent(warehouseManager, item);
    }

    @Test
    void cancellingAllRemainingQuantityClosesSalesOrder() throws Exception {
        String sales = login(createUser("close-sales", "SALES"));
        String salesManager = login(createUser("close-manager", "SALES_MANAGER"));
        String warehouseManager = login(createUser("close-warehouse", "WAREHOUSE_MANAGER"));

        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem pe = createActiveItem(UnitOfMeasure.STICK);
        CompanyItem pu = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping peMapping = createActiveMapping(customer, pe);
        CustomerItemMapping puMapping = createActiveMapping(customer, pu);
        createActivePrice(peMapping, UnitOfMeasure.STICK, "100");
        createActivePrice(puMapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, pe, "10");
        postOpeningBalance(warehouseManager, pu, "10");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer,
                line(peMapping, "3", UnitOfMeasure.STICK) + ", " + line(puMapping, "2", UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());
        List<Long> lineIds = findLineIds(salesOrderId);

        Long cancellationId = createSubmittedCancellation(sales, salesOrderId,
                cancellationLine(lineIds.get(0), "3") + ", " + cancellationLine(lineIds.get(1), "2"));
        approveCancellation(salesManager, cancellationId).andExpect(status().isOk());

        // 所有明細都沒有餘量可取消：SO 結案，預留全部釋放
        getOrder(sales, salesOrderId).andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(reservedQty(pe)).isEqualByComparingTo("0");
        assertThat(reservedQty(pu)).isEqualByComparingTo("0");

        // 結案後不能再申請取消
        createCancellation(sales, salesOrderId, cancellationLine(lineIds.get(0), "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_STATUS_CONFLICT"));

        assertConsistent(warehouseManager, pe, pu);
    }

    @Test
    void cancellationQuantityCannotExceedRemaining() throws Exception {
        String sales = login(createUser("exceed-sales", "SALES"));
        String salesManager = login(createUser("exceed-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        // 草稿 SO 不能申請餘量取消，草稿要放棄請用 SO 本身的取消
        SalesOrderVersion draft = createDraft(sales, draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS)));
        createCancellation(sales, draft.id(), cancellationLine(findLineIds(draft.id()).get(0), "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_STATUS_CONFLICT"));

        submit(sales, draft).andExpect(status().isOk());
        approve(salesManager, draft.id()).andExpect(status().isOk());
        Long lineId = findLineIds(draft.id()).get(0);

        createCancellation(sales, draft.id(), cancellationLine(lineId, "6"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_CANCELLATION_QUANTITY_EXCEEDED"));

        // PCS 不允許小數
        createCancellation(sales, draft.id(), cancellationLine(lineId, "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SO_LINE_INVALID"));
    }

    @Test
    void onlyOneOpenCancellationAndWithdrawFreesTheSlot() throws Exception {
        String sales = login(createUser("open-sales", "SALES"));
        String salesManager = login(createUser("open-manager", "SALES_MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());
        Long lineId = findLineIds(salesOrderId).get(0);

        MvcResult created = createCancellation(sales, salesOrderId, cancellationLine(lineId, "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.cancellationNumber").exists())
                .andReturn();
        Long cancellationId = readLong(created, "$.id");

        // 同一張 SO 同時只能有一張未完成的取消申請（Q9）
        createCancellation(sales, salesOrderId, cancellationLine(lineId, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_CANCELLATION_PENDING_EXISTS"));

        mockMvc.perform(post("/api/v1/sales-order-cancellations/{id}/withdraw", cancellationId)
                        .header("Authorization", bearer(sales))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d}
                                """.formatted(readLong(created, "$.version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        createCancellation(sales, salesOrderId, cancellationLine(lineId, "1"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sales-orders/{id}/cancellations", salesOrderId)
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cancellationCreatedBySalesManagerIsApprovedByManager() throws Exception {
        String sales = login(createUser("perm-sales", "SALES"));
        String salesManager = login(createUser("perm-sales-manager", "SALES_MANAGER"));
        String manager = login(createUser("perm-manager", "MANAGER"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CustomerItemMapping mapping = createActiveMapping(customer, createActiveItem(UnitOfMeasure.PCS));
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, "5", UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());

        // 業務主管自己建立並送審的取消申請，不能自己核准，改由營運主管核准
        Long cancellationId = createSubmittedCancellation(salesManager, salesOrderId,
                cancellationLine(findLineIds(salesOrderId).get(0), "1"));

        approveCancellation(salesManager, cancellationId).andExpect(status().isForbidden());
        approveCancellation(manager, cancellationId).andExpect(status().isOk());
    }
}
