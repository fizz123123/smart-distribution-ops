package com.smartdistribution.receivable;

import com.smartdistribution.fulfillment.DeliveryOrderTestSupport;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 應收整合測試共用的資料準備；客戶、料號、SO、DO 與帳號沿用 SO 與 DO 測試的工具
 * 應收的清理在 DeliveryOrderTestSupport，因為應收是 DO 過帳時建立的
 */
public abstract class ReceivableTestSupport extends DeliveryOrderTestSupport {

    /**
     * SO_MAIN：PE 10 支 @100、PU 5 個 @200，庫存剛好足夠，核准後全數預留
     * 一次全部出貨時，未稅 2,000、稅額 100、含稅 2,100
     */
    protected MainOrder createMainOrder(String warehouseManager, String salesManager, PaymentTerm paymentTerm)
            throws Exception {
        String sales = login(createUser("ar-sales", "SALES"));
        Customer customer = createActiveCustomer(paymentTerm);
        CompanyItem pe = createActiveItem(UnitOfMeasure.PCS);
        CompanyItem pu = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping peMapping = createActiveMapping(customer, pe);
        CustomerItemMapping puMapping = createActiveMapping(customer, pu);
        createActivePrice(peMapping, UnitOfMeasure.PCS, "100");
        createActivePrice(puMapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, pe, "10");
        postOpeningBalance(warehouseManager, pu, "5");

        Long salesOrderId = createSubmittedOrder(sales, draftBody(
                customer,
                line(peMapping, "10", UnitOfMeasure.PCS) + "," + line(puMapping, "5", UnitOfMeasure.PCS)
        ));
        approve(salesManager, salesOrderId).andExpect(status().isOk());
        List<Long> lineIds = findLineIds(salesOrderId);

        return new MainOrder(salesOrderId, lineIds.get(0), lineIds.get(1), pe, pu, customer, sales);
    }

    /**
     * 通知出貨 PE、PU，倉管依實際量揀貨後過帳，回傳 DO ID
     */
    protected Long shipAndPost(
            MainOrder order,
            String warehouse,
            String peQuantity,
            String puQuantity,
            LocalDate actualShipmentDate
    ) throws Exception {
        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(),
                order.salesOrderId(),
                doLine(order.peLineId(), peQuantity) + "," + doLine(order.puLineId(), puQuantity)
        );
        List<Long> lineIds = findDeliveryOrderLineIds(deliveryOrderId);
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), peQuantity) + "," + actualLine(lineIds.get(1), puQuantity))
                .andExpect(status().isOk());
        postShipment(warehouse, deliveryOrderId, actualShipmentDate).andExpect(status().isOk());

        return deliveryOrderId;
    }

    protected Long findReceivableId(Long deliveryOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM receivable WHERE delivery_order_id = ?",
                Long.class,
                deliveryOrderId
        );
    }

    protected int countReceivables(Long deliveryOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM receivable WHERE delivery_order_id = ?",
                Integer.class,
                deliveryOrderId
        );
    }

    protected ResultActions getReceivable(String token, Long receivableId) throws Exception {
        return mockMvc.perform(get("/api/v1/receivables/{id}", receivableId)
                .header("Authorization", bearer(token)));
    }

    protected Long receivableVersion(String token, Long receivableId) throws Exception {
        return readLong(getReceivable(token, receivableId).andExpect(status().isOk()).andReturn(), "$.version");
    }

    protected String receivableNumber(Long receivableId) {
        return jdbcTemplate.queryForObject(
                "SELECT receivable_number FROM receivable WHERE id = ?",
                String.class,
                receivableId
        );
    }

    //每次產生不同的發票號碼，避免和開發資料庫裡的資料撞號
    protected static String randomInvoiceNumber() {
        return "AB%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
    }

    protected ResultActions confirm(String token, Long receivableId, String invoiceNumber, LocalDate invoiceDate)
            throws Exception {
        return confirm(token, receivableId, invoiceNumber, invoiceDate, receivableVersion(token, receivableId));
    }

    protected ResultActions confirm(
            String token,
            Long receivableId,
            String invoiceNumber,
            LocalDate invoiceDate,
            Long version
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/receivables/{id}/confirm", receivableId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"externalInvoiceNumber": "%s", "externalInvoiceDate": "%s", "version": %d}
                        """.formatted(invoiceNumber, invoiceDate, version)));
    }

    protected ResultActions correctExternalInvoice(String token, Long receivableId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/receivables/{id}/external-invoice", receivableId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    //確認成正式應收，發票號碼隨機產生
    protected void confirmReceivable(String financeToken, Long receivableId) throws Exception {
        confirm(financeToken, receivableId, randomInvoiceNumber(), today()).andExpect(status().isOk());
    }

    protected String allocation(Long receivableId, String amount) {
        return """
                {"receivableId": %d, "allocatedAmount": %s}
                """.formatted(receivableId, amount);
    }

    protected ResultActions pay(
            String token,
            String clientRequestId,
            Long customerId,
            String amount,
            LocalDate receivedDate,
            String allocations
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "clientRequestId": "%s",
                          "customerId": %d,
                          "receivedDate": "%s",
                          "amount": %s,
                          "method": "BANK_TRANSFER",
                          "referenceNumber": "BANK-TEST",
                          "allocations": [%s]
                        }
                        """.formatted(clientRequestId, customerId, receivedDate, amount, allocations)));
    }

    //今天收款，每次產生新的 clientRequestId，回傳收款 ID
    protected Long payToday(String token, Long customerId, String amount, String allocations) throws Exception {
        return readLong(
                pay(token, UUID.randomUUID().toString(), customerId, amount, today(), allocations)
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.id"
        );
    }

    protected ResultActions getPayment(String token, Long paymentId) throws Exception {
        return mockMvc.perform(get("/api/v1/payments/{id}", paymentId)
                .header("Authorization", bearer(token)));
    }

    protected ResultActions voidPayment(String token, Long paymentId, String reason, Long version) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/{id}/void", paymentId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s", "version": %d}
                        """.formatted(reason, version)));
    }

    protected int countPayments(Long customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE customer_id = ?",
                Integer.class,
                customerId
        );
    }

    /**
     * @param salesToken 建立這張 SO 的業務，通知出貨用它
     */
    protected record MainOrder(
            Long salesOrderId,
            Long peLineId,
            Long puLineId,
            CompanyItem pe,
            CompanyItem pu,
            Customer customer,
            String salesToken
    ) {
    }
}
