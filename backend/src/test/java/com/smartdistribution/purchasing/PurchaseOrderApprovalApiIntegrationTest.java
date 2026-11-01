package com.smartdistribution.purchasing;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PO 核准：計入採購在途、產生正式 PDF、核准前提示與核准限制（AT-032）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseOrderApprovalApiIntegrationTest extends PurchaseOrderTestSupport {

    @Test
    void approvalMovesQuantityToInTransitAndCreatesOnePdf() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        Long purchaseOrderId = createSubmittedPo(
                purchasing,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );

        approvePo(purchasingManager, purchaseOrderId).andExpect(status().isOk());

        getPo(purchasing, purchaseOrderId)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());

        //SO 詳情：已規劃未下單轉成採購在途
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.plannedNotOrderedQty").value(0))
                .andExpect(jsonPath("$.lines[0].supply.inTransitQty").value(3))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(0));

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_file WHERE document_type = 'PURCHASE_ORDER' AND source_id = ?",
                Integer.class,
                purchaseOrderId
        );
        assertThat(documentCount).isEqualTo(1);

        String poNumber = jdbcTemplate.queryForObject(
                "SELECT po_number FROM purchase_order WHERE id = ?", String.class, purchaseOrderId
        );
        MvcResult download = mockMvc.perform(get("/api/v1/purchase-orders/{id}/document", purchaseOrderId)
                        .header("Authorization", bearer(purchasing)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(poNumber + ".pdf")))
                .andReturn();
        byte[] pdf = download.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        //最近一次核准單價
        mockMvc.perform(get("/api/v1/purchase-orders/last-price")
                        .header("Authorization", bearer(purchasing))
                        .param("supplierId", supplier.getId().toString())
                        .param("companyItemId", order.item().getId().toString())
                        .param("unit", "PCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPriceExcludingTax").value(120));

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void reducedDemandAndPriceChangeAreWarningsButDoNotBlockApproval() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        //先核准一張單價 100 的一般補庫 PO，作為「最近一次核准單價」
        Long earlier = createSubmittedPo(purchasing, poBody(supplier, poLine(order.item(), null, "1", "100")));
        approvePo(purchasingManager, earlier).andExpect(status().isOk());

        Long purchaseOrderId = createSubmittedPo(
                purchasing,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );

        //送審後客戶取消 2 個：剩餘需求 3、預留 2，扣掉這張 PO 後只缺 1
        Long cancellationId = createSubmittedCancellation(
                order.salesToken(),
                order.salesOrderId(),
                cancellationLine(order.salesOrderLineId(), "2")
        );
        approveCancellation(salesManager, cancellationId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/approval-requests/{id}", findPendingPoApprovalId(purchaseOrderId))
                        .header("Authorization", bearer(purchasingManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasItem(containsString("最近一次核准單價 100"))))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("多出的 2 到貨後成為一般庫存"))));

        approvePo(purchasingManager, purchaseOrderId).andExpect(status().isOk());

        getPo(purchasing, purchaseOrderId).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void inactiveSupplierBlocksApproval() throws Exception {
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);

        Long purchaseOrderId = createSubmittedPo(purchasing, poBody(supplier, poLine(item, null, "5", "50")));

        Supplier stored = supplierRepository.findById(supplier.getId()).orElseThrow();
        stored.deactivate();
        supplierRepository.saveAndFlush(stored);

        approvePo(purchasingManager, purchaseOrderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_MASTER_DATA_INACTIVE"));

        //核准整個回滾：PO 仍待核准，也沒有產生 PDF
        getPo(purchasing, purchaseOrderId).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_file WHERE document_type = 'PURCHASE_ORDER' AND source_id = ?",
                Integer.class,
                purchaseOrderId
        );
        assertThat(documentCount).isZero();
    }

    @Test
    void purchasingManagerCannotApproveOwnPoButManagerCan() throws Exception {
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        String manager = login(createUser("manager", "MANAGER"));
        Supplier supplier = createActiveSupplier();
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);

        //採購主管包含採購的權限，可以自己建 PO 並送審
        Long purchaseOrderId = createSubmittedPo(purchasingManager, poBody(supplier, poLine(item, null, "5", "50")));

        approvePo(purchasingManager, purchaseOrderId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_SELF_APPROVAL_FORBIDDEN"));

        //營運主管可以替代核准
        approvePo(manager, purchaseOrderId).andExpect(status().isOk());
        getPo(manager, purchaseOrderId).andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
