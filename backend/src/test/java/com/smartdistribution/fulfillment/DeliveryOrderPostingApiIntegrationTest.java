package com.smartdistribution.fulfillment;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完成揀貨、三聯出貨單、出貨過帳與作廢（AT-042～AT-046）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryOrderPostingApiIntegrationTest extends DeliveryOrderTestSupport {

    @Test
    void partialPickIsPostedAndTheRestShippedLaterClosesSalesOrder() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        Long first = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "5"));

        //只找到 4 個：以實際量進入 READY，少出的 1 個不再被這張 DO 占用
        ready(warehouse, first, actualLine(findDeliveryOrderLineIds(first).getFirst(), "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.ready.at").isNotEmpty())
                .andExpect(jsonPath("$.lines[0].actualBaseQty").value(4));

        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.deliveringQty").value(4));

        //差額可以再通知出貨
        Long second = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "1"));

        postShipment(warehouse, first, today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.actualShipmentDate").value(today().toString()))
                .andExpect(jsonPath("$.posted.at").isNotEmpty());

        //實體與預留同時減少 4，可用量不變
        assertThat(onHandQty(order.item())).isEqualByComparingTo("1");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("1");
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.lines[0].supply.shippedQty").value(4))
                .andExpect(jsonPath("$.lines[0].supply.deliveringQty").value(1));

        //重複過帳被擋下，庫存不再變動
        postShipment(warehouse, first, today())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_STATUS_CONFLICT"));
        assertThat(onHandQty(order.item())).isEqualByComparingTo("1");

        ready(warehouse, second, actualLine(findDeliveryOrderLineIds(second).getFirst(), "1"))
                .andExpect(status().isOk());
        postShipment(warehouse, second, today().minusDays(1)).andExpect(status().isOk());

        //全部出完，SO 履約結案
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.lines[0].supply.shippedQty").value(5));
        assertThat(onHandQty(order.item())).isEqualByComparingTo("0");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("0");

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void lineNotFoundIsReportedAsZeroAndLeftOffTheDeliveryNote() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String sales = login(createUser("do-sales", "SALES"));

        //一張 SO 兩個料號，各訂 3、各有庫存 3
        Customer customer = createActiveCustomer(PaymentTerm.NET_30);
        CompanyItem first = createActiveItem(UnitOfMeasure.PCS);
        CompanyItem second = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping firstMapping = createActiveMapping(customer, first);
        CustomerItemMapping secondMapping = createActiveMapping(customer, second);
        createActivePrice(firstMapping, UnitOfMeasure.PCS, "100");
        createActivePrice(secondMapping, UnitOfMeasure.PCS, "100");
        postOpeningBalance(warehouseManager, first, "3");
        postOpeningBalance(warehouseManager, second, "3");
        Long salesOrderId = createSubmittedOrder(sales, draftBody(
                customer,
                line(firstMapping, "3", UnitOfMeasure.PCS) + "," + line(secondMapping, "3", UnitOfMeasure.PCS)
        ));
        approve(salesManager, salesOrderId).andExpect(status().isOk());
        List<Long> salesOrderLineIds = findLineIds(salesOrderId);

        Long deliveryOrderId = createDeliveryOrder(
                sales, salesOrderId, doLine(salesOrderLineIds.get(0), "3") + "," + doLine(salesOrderLineIds.get(1), "3")
        );
        List<Long> lineIds = findDeliveryOrderLineIds(deliveryOrderId);

        //少回報一筆、超過預計量、全部都是 0，都被擋下
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_ACTUAL_QUANTITY_INVALID"));
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), "4") + "," + actualLine(lineIds.get(1), "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_ACTUAL_QUANTITY_INVALID"));
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), "0") + "," + actualLine(lineIds.get(1), "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_ACTUAL_QUANTITY_INVALID"));

        //第二個料號整批找不到，填 0
        ready(warehouse, deliveryOrderId, actualLine(lineIds.get(0), "3") + "," + actualLine(lineIds.get(1), "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        //三聯出貨單：三頁，只印第一個料號
        byte[] pdf = mockMvc.perform(get("/api/v1/delivery-orders/{id}/document", deliveryOrderId)
                        .header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(3);
            //表格內的文字可能被斷成好幾行，比對前先拿掉所有空白與換行
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            assertThat(pageText(extractor, 1)).contains("存根聯", first.getItemCode()).doesNotContain(second.getItemCode());
            assertThat(pageText(extractor, 2)).contains("客戶聯");
            assertThat(pageText(extractor, 3)).contains("簽收聯", "收貨人簽名");
        } finally {
            reader.close();
        }

        postShipment(warehouse, deliveryOrderId, today()).andExpect(status().isOk());

        //填 0 的那一筆不扣庫存，預留仍留給這張 SO
        assertThat(onHandQty(first)).isEqualByComparingTo("0");
        assertThat(onHandQty(second)).isEqualByComparingTo("3");
        assertThat(reservedQty(second)).isEqualByComparingTo("3");
        getOrder(salesManager, salesOrderId).andExpect(jsonPath("$.status").value("APPROVED"));

        assertConsistent(warehouseManager, first, second);
    }

    private static String pageText(PdfTextExtractor extractor, int page) throws Exception {
        return extractor.getTextFromPage(page).replaceAll("\\s", "");
    }

    @Test
    void readyDeliveryOrderCanBeVoidedButPostedCannot() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        Long first = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "3"));
        ready(warehouse, first, actualLine(findDeliveryOrderLineIds(first).getFirst(), "3")).andExpect(status().isOk());

        //業務已經不能撤回揀好的 DO
        withdraw(order.salesToken(), first, deliveryOrderVersion(order.salesToken(), first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_STATUS_CONFLICT"));

        voidOrder(warehouse, first, "客戶改期", deliveryOrderVersion(warehouse, first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));

        //出貨單一併作廢，仍可下載；庫存與預留都沒變
        String documentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM document_file WHERE document_type = 'DELIVERY_ORDER' AND source_id = ?",
                String.class,
                first
        );
        assertThat(documentStatus).isEqualTo("VOIDED");
        mockMvc.perform(get("/api/v1/delivery-orders/{id}/document", first)
                        .header("Authorization", bearer(warehouse)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-voided.pdf")));
        assertThat(onHandQty(order.item())).isEqualByComparingTo("5");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("5");
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.deliveringQty").value(0));

        Long second = createDeliveryOrder(order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "2"));
        ready(warehouse, second, actualLine(findDeliveryOrderLineIds(second).getFirst(), "2")).andExpect(status().isOk());
        postShipment(warehouse, second, today()).andExpect(status().isOk());

        voidOrder(warehouse, second, "想作廢已出貨的", deliveryOrderVersion(warehouse, second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_STATUS_CONFLICT"));

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void onlyWarehouseCanPickAndPostAndShipmentDateCannotBeInTheFuture() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "5", "5");

        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "5")
        );
        String lines = actualLine(findDeliveryOrderLineIds(deliveryOrderId).getFirst(), "5");

        //還沒揀貨不能過帳
        postShipment(warehouse, deliveryOrderId, today())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DO_STATUS_CONFLICT"));

        ready(order.salesToken(), deliveryOrderId, lines).andExpect(status().isForbidden());
        ready(warehouse, deliveryOrderId, lines).andExpect(status().isOk());

        postShipment(order.salesToken(), deliveryOrderId, today()).andExpect(status().isForbidden());
        postShipment(warehouse, deliveryOrderId, today().plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DO_SHIPMENT_DATE_INVALID"));

        assertThat(onHandQty(order.item())).isEqualByComparingTo("5");
        assertConsistent(warehouseManager, order.item());
    }
}
