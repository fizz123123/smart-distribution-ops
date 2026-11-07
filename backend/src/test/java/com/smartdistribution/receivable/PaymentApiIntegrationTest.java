package com.smartdistribution.receivable;

import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收款、核銷與作廢收款（AT-053～AT-056，以及 AT-058 的「已收清不列入」）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void partialThenFullPaymentSettlesTheReceivable() throws Exception {
        Users users = users();
        //DUE_0、今天出貨 → 今天到期，算即將到期
        MainOrder order = createMainOrder(users.warehouseManager(), users.salesManager(), PaymentTerm.DUE_0);
        Long receivableId = findReceivableId(shipAndPost(order, users.warehouse(), "10", "5", today()));
        confirmReceivable(users.finance(), receivableId);

        payableReceivables(users.finance(), order)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(receivableId))
                .andExpect(jsonPath("$[0].balance").value(2100));

        payToday(users.finance(), order.customer().getId(), "1000", allocation(receivableId, "1000"));
        getReceivable(users.finance(), receivableId)
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.paidAmount").value(1000))
                .andExpect(jsonPath("$.balance").value(1100))
                .andExpect(jsonPath("$.dueStatus").value("DUE_SOON"))
                .andExpect(jsonPath("$.allocations.length()").value(1));

        payToday(users.finance(), order.customer().getId(), "1100", allocation(receivableId, "1100"));
        getReceivable(users.finance(), receivableId)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(2100))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.dueStatus").value(nullValue()));

        //已收清的不再列入即將到期，也不會出現在收款時的清單
        mockMvc.perform(get("/api/v1/receivables")
                        .param("dueStatus", "DUE_SOON")
                        .param("keyword", receivableNumber(receivableId))
                        .header("Authorization", bearer(users.finance())))
                .andExpect(jsonPath("$.content.length()").value(0));
        payableReceivables(users.finance(), order).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onePaymentSettlesTwoReceivablesOfTheSameCustomer() throws Exception {
        Users users = users();
        MainOrder order = createMainOrder(users.warehouseManager(), users.salesManager(), PaymentTerm.NET_30);
        //分兩張 DO 出貨：PE 10 支一張、PU 5 個一張，各 1,050
        Long first = findReceivableId(shipOne(order, order.peLineId(), "10", users.warehouse()));
        Long second = findReceivableId(shipOne(order, order.puLineId(), "5", users.warehouse()));
        confirmReceivable(users.finance(), first);
        confirmReceivable(users.finance(), second);

        payableReceivables(users.finance(), order)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[1].id").value(second));

        Long paymentId = payToday(
                users.finance(),
                order.customer().getId(),
                "2100",
                allocation(first, "1050") + "," + allocation(second, "1050")
        );

        getPayment(users.finance(), paymentId)
                .andExpect(jsonPath("$.paymentNumber", containsString("PAY-")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount").value(2100))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[0].receivableId").value(first))
                .andExpect(jsonPath("$.allocations[1].receivableId").value(second));
        getReceivable(users.finance(), first).andExpect(jsonPath("$.status").value("PAID"));
        getReceivable(users.finance(), second).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void invalidPaymentIsRejectedWithoutLeavingAnything() throws Exception {
        Users users = users();
        MainOrder order = createMainOrder(users.warehouseManager(), users.salesManager(), PaymentTerm.NET_30);
        MainOrder otherCustomerOrder = createMainOrder(users.warehouseManager(), users.salesManager(), PaymentTerm.NET_30);
        Long confirmed = findReceivableId(shipOne(order, order.peLineId(), "10", users.warehouse()));
        Long draft = findReceivableId(shipOne(order, order.puLineId(), "5", users.warehouse()));
        Long otherCustomers = findReceivableId(shipAndPost(otherCustomerOrder, users.warehouse(), "10", "5", today()));
        confirmReceivable(users.finance(), confirmed);
        confirmReceivable(users.finance(), otherCustomers);
        Long customerId = order.customer().getId();

        //超過未收餘額 1,050
        payNew(users.finance(), customerId, "1100", allocation(confirmed, "1100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_EXCEEDS_BALANCE"))
                .andExpect(jsonPath("$.message", containsString("最多還能核銷 1050")));
        //別的客戶的應收
        payNew(users.finance(), customerId, "2100", allocation(confirmed, "1050") + "," + allocation(otherCustomers, "1050"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIVABLE_INVALID"))
                .andExpect(jsonPath("$.message", containsString("不是這個客戶的應收")));
        //還沒確認的應收
        payNew(users.finance(), customerId, "1050", allocation(draft, "1050"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIVABLE_INVALID"))
                .andExpect(jsonPath("$.message", containsString("還沒確認")));
        //分配加總不等於收款金額；同一筆應收分兩次
        payNew(users.finance(), customerId, "1000", allocation(confirmed, "900"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        payNew(users.finance(), customerId, "1000", allocation(confirmed, "500") + "," + allocation(confirmed, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        //收款日期在未來；金額有小數
        pay(users.finance(), UUID.randomUUID().toString(), customerId, "500", today().plusDays(1), allocation(confirmed, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        payNew(users.finance(), customerId, "500.5", allocation(confirmed, "500.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        //整筆都沒寫入：沒有收款，應收餘額不變
        assertThat(countPayments(customerId)).isZero();
        getReceivable(users.finance(), confirmed)
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.balance").value(1050));
    }

    @Test
    void voidingPaymentRestoresTheBalance() throws Exception {
        Users users = users();
        String financeManager = login(createUser("finance-manager", "FINANCE_MANAGER"));
        MainOrder order = createMainOrder(users.warehouseManager(), users.salesManager(), PaymentTerm.NET_30);
        Long receivableId = findReceivableId(shipAndPost(order, users.warehouse(), "10", "5", today()));
        confirmReceivable(users.finance(), receivableId);
        Long firstPayment = payToday(users.finance(), order.customer().getId(), "1000", allocation(receivableId, "1000"));
        Long secondPayment = payToday(users.finance(), order.customer().getId(), "1100", allocation(receivableId, "1100"));
        getReceivable(users.finance(), receivableId).andExpect(jsonPath("$.status").value("PAID"));

        //財務不能作廢
        voidPayment(users.finance(), secondPayment, "金額登錯", paymentVersion(users.finance(), secondPayment))
                .andExpect(status().isForbidden());

        voidPayment(financeManager, secondPayment, "金額登錯", paymentVersion(users.finance(), secondPayment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.voidance.reason").value("金額登錯"))
                .andExpect(jsonPath("$.voidance.by.id").isNotEmpty())
                .andExpect(jsonPath("$.allocations.length()").value(1));

        //核銷失效，未收餘額恢復；核銷紀錄仍在，標示收款已作廢
        getReceivable(users.finance(), receivableId)
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balance").value(1100))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andExpect(jsonPath("$.allocations[1].paymentStatus").value("VOIDED"));

        //不能作廢兩次
        voidPayment(financeManager, secondPayment, "再作廢一次", paymentVersion(users.finance(), secondPayment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATUS_CONFLICT"));

        voidPayment(financeManager, firstPayment, "客戶退票", paymentVersion(users.finance(), firstPayment))
                .andExpect(status().isOk());
        getReceivable(users.finance(), receivableId)
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.balance").value(2100));
    }

    @Test
    void resendingTheSameRequestDoesNotPayTwice() throws Exception {
        Users users = users();
        String manager = login(createUser("manager", "MANAGER"));
        MainOrder order = createMainOrder(users.warehouseManager(), users.salesManager(), PaymentTerm.NET_30);
        Long receivableId = findReceivableId(shipAndPost(order, users.warehouse(), "10", "5", today()));
        confirmReceivable(users.finance(), receivableId);
        String clientRequestId = UUID.randomUUID().toString();
        Long customerId = order.customer().getId();

        Long paymentId = readLong(
                pay(users.finance(), clientRequestId, customerId, "1000", today(), allocation(receivableId, "1000"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.id"
        );

        //網路重試：同一個 clientRequestId 再送一次，回 200 與原本那筆
        pay(users.finance(), clientRequestId, customerId, "1000", today(), allocation(receivableId, "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId));
        assertThat(countPayments(customerId)).isEqualTo(1);
        getReceivable(users.finance(), receivableId).andExpect(jsonPath("$.balance").value(1100));

        //營運主管看得到收款，但不能登錄
        getPayment(manager, paymentId).andExpect(status().isOk());
        payNew(manager, customerId, "100", allocation(receivableId, "100")).andExpect(status().isForbidden());
    }

    // ===== 工具 =====

    private Users users() throws Exception {
        return new Users(
                login(createUser("wh-manager", "WAREHOUSE_MANAGER")),
                login(createUser("sales-manager", "SALES_MANAGER")),
                login(createUser("warehouse", "WAREHOUSE")),
                login(createUser("finance", "FINANCE"))
        );
    }

    //只出一筆 SO 明細的 DO，揀貨、過帳，回傳 DO ID
    private Long shipOne(MainOrder order, Long salesOrderLineId, String quantity, String warehouse) throws Exception {
        Long deliveryOrderId = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(salesOrderLineId, quantity));
        List<Long> lineIds = findDeliveryOrderLineIds(deliveryOrderId);
        ready(warehouse, deliveryOrderId, actualLine(lineIds.getFirst(), quantity)).andExpect(status().isOk());
        postShipment(warehouse, deliveryOrderId, today()).andExpect(status().isOk());

        return deliveryOrderId;
    }

    private ResultActions payNew(String token, Long customerId, String amount, String allocations) throws Exception {
        return pay(token, UUID.randomUUID().toString(), customerId, amount, today(), allocations);
    }

    private ResultActions payableReceivables(String token, MainOrder order) throws Exception {
        return mockMvc.perform(get("/api/v1/customers/{id}/open-receivables", order.customer().getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private Long paymentVersion(String token, Long paymentId) throws Exception {
        return readLong(getPayment(token, paymentId).andExpect(status().isOk()).andReturn(), "$.version");
    }

    private record Users(String warehouseManager, String salesManager, String warehouse, String finance) {
    }
}
