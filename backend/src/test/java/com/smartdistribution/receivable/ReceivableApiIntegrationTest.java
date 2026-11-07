package com.smartdistribution.receivable;

import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DO 過帳時建立應收草稿（AT-050、AT-051）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceivableApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void postingCreatesOneDraftFromActualQuantitiesAndSnapshotPrices() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);

        Long deliveryOrderId = shipAndPost(order, warehouse, "10", "5", today());
        Long receivableId = findReceivableId(deliveryOrderId);

        //PE 10 × 100 ＋ PU 5 × 200 ＝ 2,000；稅 100；含稅 2,100
        getReceivable(finance, receivableId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivableNumber", startsWith("AR-")))
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.customer.id").value(order.customer().getId()))
                .andExpect(jsonPath("$.deliveryOrder.id").value(deliveryOrderId))
                .andExpect(jsonPath("$.deliveryOrder.actualShipmentDate").value(today().toString()))
                .andExpect(jsonPath("$.salesOrder.id").value(order.salesOrderId()))
                .andExpect(jsonPath("$.paymentTerm").value("NET_30"))
                .andExpect(jsonPath("$.dueDate").value(today().plusDays(30).toString()))
                .andExpect(jsonPath("$.untaxedAmount").value(2000))
                .andExpect(jsonPath("$.taxAmount").value(100))
                .andExpect(jsonPath("$.totalAmount").value(2100))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].quantity").value(10))
                .andExpect(jsonPath("$.lines[0].unitPriceExcludingTax").value(100))
                .andExpect(jsonPath("$.lines[0].lineAmountExcludingTax").value(1000))
                .andExpect(jsonPath("$.lines[1].lineAmountExcludingTax").value(1000));

        //重複過帳被 DO 擋下，不會有第二筆應收
        postShipment(warehouse, deliveryOrderId, today())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_STATUS_CONFLICT"));
        assertThat(countReceivables(deliveryOrderId)).isEqualTo(1);

        //清單用 DO 編號也找得到
        String doNumber = jdbcTemplate.queryForObject(
                "SELECT do_number FROM delivery_order WHERE id = ?", String.class, deliveryOrderId
        );
        mockMvc.perform(get("/api/v1/receivables")
                        .param("keyword", doNumber)
                        .header("Authorization", bearer(finance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(receivableId))
                .andExpect(jsonPath("$.content[0].totalAmount").value(2100));
    }

    @Test
    void dueDateFollowsPaymentTermFromActualShipmentDate() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        //補登前幾天出貨的 DO，到期日從實際出貨日起算，不是從過帳那天
        LocalDate shippedOn = today().minusDays(3);

        for (PaymentTerm paymentTerm : List.of(PaymentTerm.DUE_0, PaymentTerm.NET_30, PaymentTerm.NET_60)) {
            MainOrder order = createMainOrder(warehouseManager, salesManager, paymentTerm);
            Long deliveryOrderId = shipAndPost(order, warehouse, "10", "5", shippedOn);

            getReceivable(finance, findReceivableId(deliveryOrderId))
                    .andExpect(jsonPath("$.paymentTerm").value(paymentTerm.name()))
                    .andExpect(jsonPath("$.dueDate").value(shippedOn.plusDays(paymentTerm.getDays()).toString()));
        }
    }

    @Test
    void eachPostedDeliveryOrderBillsOnlyWhatWasShipped() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);

        //第一張：PE 全出、PU 只找到 4 個
        Long firstDeliveryOrderId = createDeliveryOrder(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.peLineId(), "10") + "," + doLine(order.puLineId(), "5")
        );
        List<Long> firstLineIds = findDeliveryOrderLineIds(firstDeliveryOrderId);
        ready(warehouse, firstDeliveryOrderId, actualLine(firstLineIds.get(0), "10") + "," + actualLine(firstLineIds.get(1), "4"))
                .andExpect(status().isOk());
        postShipment(warehouse, firstDeliveryOrderId, today()).andExpect(status().isOk());

        //1,000 ＋ 800 ＝ 1,800；稅 90；含稅 1,890
        getReceivable(finance, findReceivableId(firstDeliveryOrderId))
                .andExpect(jsonPath("$.untaxedAmount").value(1800))
                .andExpect(jsonPath("$.taxAmount").value(90))
                .andExpect(jsonPath("$.totalAmount").value(1890));

        //第二張：只出剩下的 PU 1 個
        Long secondDeliveryOrderId = createDeliveryOrder(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.puLineId(), "1")
        );
        ready(warehouse, secondDeliveryOrderId, actualLine(findDeliveryOrderLineIds(secondDeliveryOrderId).getFirst(), "1"))
                .andExpect(status().isOk());
        postShipment(warehouse, secondDeliveryOrderId, today()).andExpect(status().isOk());

        getReceivable(finance, findReceivableId(secondDeliveryOrderId))
                .andExpect(jsonPath("$.untaxedAmount").value(200))
                .andExpect(jsonPath("$.taxAmount").value(10))
                .andExpect(jsonPath("$.totalAmount").value(210))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].salesOrderLineId").value(order.puLineId()));
    }

    @Test
    void lineShippedAsZeroIsLeftOffTheReceivable() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.DUE_0);

        //PU 整批找不到，填 0
        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.peLineId(), "10") + "," + doLine(order.puLineId(), "5")
        );
        List<Long> lineIds = findDeliveryOrderLineIds(deliveryOrderId);
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), "10") + "," + actualLine(lineIds.get(1), "0"))
                .andExpect(status().isOk());
        postShipment(warehouse, deliveryOrderId, today()).andExpect(status().isOk());

        getReceivable(finance, findReceivableId(deliveryOrderId))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].salesOrderLineId").value(order.peLineId()))
                .andExpect(jsonPath("$.totalAmount").value(1050));
    }

    @Test
    void failingToCreateReceivableRollsBackTheWholePosting() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);

        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.peLineId(), "10") + "," + doLine(order.puLineId(), "5")
        );
        List<Long> lineIds = findDeliveryOrderLineIds(deliveryOrderId);
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), "10") + "," + actualLine(lineIds.get(1), "5"))
                .andExpect(status().isOk());

        //先放一筆指向這張 DO 的應收，讓建立應收失敗
        jdbcTemplate.update(
                """
                INSERT INTO receivable (delivery_order_id, do_number, sales_order_id, so_number, customer_id,
                    status, payment_term, actual_shipment_date, due_date,
                    untaxed_amount, tax_amount, total_amount, version, created_at, updated_at)
                VALUES (?, 'DO-TEST', ?, 'SO-TEST', ?, 'PENDING_CONFIRMATION', 'NET_30', CURDATE(), CURDATE(),
                    0, 0, 0, 0, NOW(), NOW())
                """,
                deliveryOrderId,
                order.salesOrderId(),
                order.customer().getId()
        );

        postShipment(warehouse, deliveryOrderId, today())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECEIVABLE_ALREADY_EXISTS"));

        //DO 仍是 READY，庫存與預留都沒有扣
        getDeliveryOrder(warehouse, deliveryOrderId).andExpect(jsonPath("$.status").value("READY"));
        assertThat(onHandQty(order.pe())).isEqualByComparingTo("10");
        assertThat(reservedQty(order.pe())).isEqualByComparingTo("10");
        assertConsistent(warehouseManager, order.pe(), order.pu());
    }

    @Test
    void onlyFinanceAndManagerCanSeeReceivables() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String financeManager = login(createUser("finance-manager", "FINANCE_MANAGER"));
        String manager = login(createUser("manager", "MANAGER"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);
        Long receivableId = findReceivableId(shipAndPost(order, warehouse, "10", "5", today()));

        getReceivable(financeManager, receivableId).andExpect(status().isOk());
        getReceivable(manager, receivableId).andExpect(status().isOk());
        getReceivable(order.salesToken(), receivableId).andExpect(status().isForbidden());
        getReceivable(warehouse, receivableId).andExpect(status().isForbidden());
    }
}
