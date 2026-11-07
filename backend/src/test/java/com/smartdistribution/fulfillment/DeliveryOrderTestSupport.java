package com.smartdistribution.fulfillment;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.sales.SalesOrderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DO 整合測試共用的資料準備與清理；客戶、料號、SO 與帳號沿用 SO 測試的工具
 */
public abstract class DeliveryOrderTestSupport extends SalesOrderTestSupport {

    //SO 測試的清理（父類別）只認得 SO；DO 只存 SO 的 ID，沒有外鍵，先後都可以
    @BeforeEach
    @AfterEach
    protected void cleanDeliveryOrders() {
        String testOrders = """
                SELECT so.id FROM sales_order so
                JOIN customer customer ON customer.id = so.customer_id
                WHERE customer.name LIKE ?
                """;

        //DO 過帳會建立應收；應收只存 SO 與 DO 的 ID，沒有外鍵，和 DO 一起清掉
        //核銷明細對應收有外鍵，所以先刪核銷與收款
        jdbcTemplate.update(
                """
                DELETE allocation FROM payment_allocation allocation
                JOIN payment payment ON payment.id = allocation.payment_id
                JOIN customer customer ON customer.id = payment.customer_id
                WHERE customer.name LIKE ?
                """,
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE payment FROM payment payment
                JOIN customer customer ON customer.id = payment.customer_id
                WHERE customer.name LIKE ?
                """,
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE FROM audit_log
                WHERE target_type = 'RECEIVABLE'
                  AND target_id IN (SELECT id FROM receivable WHERE sales_order_id IN (%s))
                """.formatted(testOrders),
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE line FROM receivable_line line
                JOIN receivable receivable ON receivable.id = line.receivable_id
                WHERE receivable.sales_order_id IN (%s)
                """.formatted(testOrders),
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM receivable WHERE sales_order_id IN (%s)".formatted(testOrders),
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE FROM document_file
                WHERE document_type = 'DELIVERY_ORDER'
                  AND source_id IN (SELECT id FROM delivery_order WHERE sales_order_id IN (%s))
                """.formatted(testOrders),
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE line FROM delivery_order_line line
                JOIN delivery_order deliveryOrder ON deliveryOrder.id = line.delivery_order_id
                WHERE deliveryOrder.sales_order_id IN (%s)
                """.formatted(testOrders),
                CUSTOMER_NAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                "DELETE FROM delivery_order WHERE sales_order_id IN (%s)".formatted(testOrders),
                CUSTOMER_NAME_PREFIX + "%"
        );
    }

    /**
     * 建立一張已核准的 SO：料號期初庫存 stock 個，訂購 ordered 個
     * 例如 stock 3、ordered 5 → 預留 3
     */
    protected ApprovedOrder createApprovedOrder(String warehouseManager, String salesManager, String stock, String ordered)
            throws Exception {
        String sales = login(createUser("do-sales", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.NET_30);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");
        postOpeningBalance(warehouseManager, item, stock);

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, ordered, UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());

        return new ApprovedOrder(salesOrderId, findLineIds(salesOrderId).getFirst(), item, sales);
    }

    // ===== DO =====

    protected String doLine(Long salesOrderLineId, String quantity) {
        return """
                {"salesOrderLineId": %d, "plannedQuantity": %s}
                """.formatted(salesOrderLineId, quantity);
    }

    protected ResultActions notifyShipment(String token, Long salesOrderId, String lines) throws Exception {
        return mockMvc.perform(post("/api/v1/sales-orders/{id}/delivery-orders", salesOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"plannedShipmentDate": "2026-11-12", "lines": [%s]}
                        """.formatted(lines)));
    }

    //通知出貨並回傳 DO ID
    protected Long createDeliveryOrder(String token, Long salesOrderId, String lines) throws Exception {
        MvcResult result = notifyShipment(token, salesOrderId, lines)
                .andExpect(status().isCreated())
                .andReturn();

        return readLong(result, "$.id");
    }

    protected ResultActions getDeliveryOrder(String token, Long deliveryOrderId) throws Exception {
        return mockMvc.perform(get("/api/v1/delivery-orders/{id}", deliveryOrderId)
                .header("Authorization", bearer(token)));
    }

    protected Long deliveryOrderVersion(String token, Long deliveryOrderId) throws Exception {
        return readLong(getDeliveryOrder(token, deliveryOrderId).andExpect(status().isOk()).andReturn(), "$.version");
    }

    protected ResultActions withdraw(String token, Long deliveryOrderId, Long version) throws Exception {
        return mockMvc.perform(post("/api/v1/delivery-orders/{id}/withdraw", deliveryOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d}
                        """.formatted(version)));
    }

    protected ResultActions voidOrder(String token, Long deliveryOrderId, String reason, Long version) throws Exception {
        return mockMvc.perform(post("/api/v1/delivery-orders/{id}/void", deliveryOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s", "version": %d}
                        """.formatted(reason, version)));
    }

    // ===== 揀貨與過帳 =====

    //DO 明細 ID，依行號排序
    protected List<Long> findDeliveryOrderLineIds(Long deliveryOrderId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM delivery_order_line WHERE delivery_order_id = ? ORDER BY line_no",
                Long.class,
                deliveryOrderId
        );
    }

    protected String actualLine(Long deliveryOrderLineId, String quantity) {
        return """
                {"deliveryOrderLineId": %d, "actualQuantity": %s}
                """.formatted(deliveryOrderLineId, quantity);
    }

    protected ResultActions ready(String token, Long deliveryOrderId, String lines) throws Exception {
        return mockMvc.perform(post("/api/v1/delivery-orders/{id}/ready", deliveryOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines": [%s], "version": %d}
                        """.formatted(lines, deliveryOrderVersion(token, deliveryOrderId))));
    }

    protected ResultActions postShipment(String token, Long deliveryOrderId, LocalDate actualShipmentDate) throws Exception {
        return mockMvc.perform(post("/api/v1/delivery-orders/{id}/post", deliveryOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"actualShipmentDate": "%s", "version": %d}
                        """.formatted(actualShipmentDate, deliveryOrderVersion(token, deliveryOrderId))));
    }

    protected static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Taipei"));
    }

    /**
     * @param salesToken 建立這張 SO 的業務，通知出貨與申請取消都用它
     */
    protected record ApprovedOrder(Long salesOrderId, Long salesOrderLineId, CompanyItem item, String salesToken) {
    }
}
