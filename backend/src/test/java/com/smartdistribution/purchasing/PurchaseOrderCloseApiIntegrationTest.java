package com.smartdistribution.purchasing;

import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 取消已核准 PO 與短收結案：採購在途減少、來源 SO 的缺料重新出現、PDF 作廢（AT-036）
 * 每個情境的 SO 都是期初庫存 2、訂購 5：預留 2、缺 3，再用一張 3 個的 PO 補
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseOrderCloseApiIntegrationTest extends PurchaseOrderTestSupport {

    @Test
    void cancellingApprovedPoRestoresShortageAndVoidsPdf() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        Long purchaseOrderId = createApprovedPo(
                purchasing, purchasingManager,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );
        Long version = poVersion(purchasing, purchaseOrderId);

        //採購不能取消已核准的 PO；原因必填
        closePo(purchasing, "cancel", purchaseOrderId, "供應商停產", version)
                .andExpect(status().isForbidden());
        closePo(purchasingManager, "cancel", purchaseOrderId, " ", version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        closePo(purchasingManager, "cancel", purchaseOrderId, "供應商停產", version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.reason").value("供應商停產"))
                .andExpect(jsonPath("$.cancellation.at").isNotEmpty())
                .andExpect(jsonPath("$.lines[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.lines[0].cancelledBaseQty").value(3))
                .andExpect(jsonPath("$.lines[0].openBaseQty").value(0));

        //採購在途消失，缺的 3 個回到尚待規劃，可以另開 PO
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.inTransitQty").value(0))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(3));

        //PDF 作廢但仍可下載，檔名加上 -voided
        String documentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM document_file WHERE document_type = 'PURCHASE_ORDER' AND source_id = ?",
                String.class,
                purchaseOrderId
        );
        assertThat(documentStatus).isEqualTo("VOIDED");
        mockMvc.perform(get("/api/v1/purchase-orders/{id}/document", purchaseOrderId)
                        .header("Authorization", bearer(purchasing)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-voided.pdf")));

        //取消後不能收貨，也不能再取消一次
        postReceipt(warehouseManager, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "3", "3", "0", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_STATUS_CONFLICT"));
        closePo(purchasingManager, "cancel", purchaseOrderId, "再取消一次", poVersion(purchasing, purchaseOrderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_STATUS_CONFLICT"));

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void poWithReceiptsCannotBeCancelledAndUnreceivedPoCannotBeShortClosed() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        Long purchaseOrderId = createApprovedPo(
                purchasing, purchasingManager,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );

        //還沒收貨的 PO 不需要短收，直接取消即可
        closePo(purchasingManager, "close-short", purchaseOrderId, "不等了", poVersion(purchasing, purchaseOrderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_STATUS_CONFLICT"));

        postReceipt(warehouseManager, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "1", "1", "0", null))
                .andExpect(status().isCreated());

        closePo(purchasingManager, "cancel", purchaseOrderId, "供應商停產", poVersion(purchasing, purchaseOrderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_HAS_RECEIPTS"));

        getPo(purchasing, purchaseOrderId).andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
    }

    @Test
    void shortCloseStopsWaitingForTheRestAndKeepsReceivedStock() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        Long purchaseOrderId = createApprovedPo(
                purchasing, purchasingManager,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );

        //到貨 3 個，其中 1 個損壞：合格 2 個入庫並補到預留，還差 1 個
        postReceipt(
                warehouseManager,
                purchaseOrderId,
                receiptLine(findPoLineId(purchaseOrderId), "3", "2", "1", "外箱破損")
        ).andExpect(status().isCreated());

        //用舊版本送出會被擋下
        Long version = poVersion(purchasing, purchaseOrderId);
        closePo(purchasingManager, "close-short", purchaseOrderId, "供應商無法補貨", version - 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        closePo(purchasingManager, "close-short", purchaseOrderId, "供應商無法補貨", version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.shortClose.reason").value("供應商無法補貨"))
                .andExpect(jsonPath("$.cancellation").doesNotExist())
                .andExpect(jsonPath("$.lines[0].status").value("CLOSED_SHORT"))
                .andExpect(jsonPath("$.lines[0].acceptedBaseQty").value(2))
                .andExpect(jsonPath("$.lines[0].cancelledBaseQty").value(1))
                .andExpect(jsonPath("$.lines[0].openBaseQty").value(0));

        //已入庫的不變；不再等的 1 個回到 SO 的尚待規劃
        assertThat(onHandQty(order.item())).isEqualByComparingTo("4");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("4");
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.inTransitQty").value(0))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(1));

        //有實際交易的 PO，PDF 不作廢
        String documentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM document_file WHERE document_type = 'PURCHASE_ORDER' AND source_id = ?",
                String.class,
                purchaseOrderId
        );
        assertThat(documentStatus).isEqualTo("ACTIVE");

        assertConsistent(warehouseManager, order.item());
    }
}
