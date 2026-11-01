package com.smartdistribution.purchasing;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PO 草稿：關聯 SO 缺料、一般補庫、送審與放棄草稿（AT-030、AT-031）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseOrderApiIntegrationTest extends PurchaseOrderTestSupport {

    @Test
    void linkedDraftCountsAsPlannedAndCannotExceedUnplannedQty() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        Supplier supplier = createActiveSupplier();

        //預留 2、尚待規劃 3
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        PurchaseOrderVersion draft = createPoDraft(
                purchasing,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", null))
        );

        //SO 詳情：已規劃未下單 3，尚待規劃歸 0
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.reservedQty").value(2))
                .andExpect(jsonPath("$.lines[0].supply.plannedNotOrderedQty").value(3))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(0));

        //另一張 PO 再關聯同一筆明細，已經沒有可規劃的量
        performCreatePo(purchasing, poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "1", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_SOURCE_QUANTITY_EXCEEDED"))
                .andExpect(jsonPath("$.message", containsString("最多還能規劃 0")));

        //修改自己的草稿時排除自己：維持 3 可以，改成 4 超過
        updatePo(purchasing, draft, supplier, poLine(order.item(), order.salesOrderLineId(), "4", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("最多還能規劃 3")));
        updatePo(purchasing, draft, supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].lineAmountExcludingTax").value(360))
                .andExpect(jsonPath("$.untaxedAmount").value(360));

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void onePoCanCombineTwoSalesOrdersAndGeneralReplenishment() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        Supplier supplier = createActiveSupplier();

        ShortageOrder first = createShortageOrder(warehouseManager, salesManager, "0", "3");
        ShortageOrder second = createShortageOrder(warehouseManager, salesManager, "0", "4");

        performCreatePo(purchasing, poBody(supplier, String.join(",",
                        poLine(first.item(), first.salesOrderLineId(), "3", "100"),
                        poLine(second.item(), second.salesOrderLineId(), "4", "100"),
                        poLine(first.item(), null, "10", "100")
                )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.poNumber").value(startsWith("PO-")))
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[0].source.salesOrderLineId").value(first.salesOrderLineId()))
                .andExpect(jsonPath("$.lines[1].source.salesOrderLineId").value(second.salesOrderLineId()))
                .andExpect(jsonPath("$.lines[2].source").doesNotExist());

        //一般補庫不影響任何 SO 的供應狀態
        getOrder(salesManager, first.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.plannedNotOrderedQty").value(3));
        getOrder(salesManager, second.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.plannedNotOrderedQty").value(4));
    }

    @Test
    void draftMayMissPriceButSubmitRequiresIt() throws Exception {
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        Supplier supplier = createActiveSupplier();
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);

        PurchaseOrderVersion draft = createPoDraft(purchasing, poBody(supplier, poLine(item, null, "10", null)));

        submitPo(purchasing, draft)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PO_SUBMIT_INFO_INCOMPLETE"));

        MvcResult updated = updatePo(purchasing, draft, supplier, poLine(item, null, "10", "12.5"))
                .andExpect(status().isOk())
                .andReturn();

        submitPo(purchasing, new PurchaseOrderVersion(draft.id(), readLong(updated, "$.version")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.untaxedAmount").value(125));
    }

    @Test
    void cancelledDraftNoLongerCountsAsPlanned() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        PurchaseOrderVersion draft = createPoDraft(
                purchasing,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", null))
        );

        mockMvc.perform(post("/api/v1/purchase-orders/{id}/cancel-draft", draft.id())
                        .header("Authorization", bearer(purchasing))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d}
                                """.formatted(draft.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.plannedNotOrderedQty").value(0))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(3));
    }

    @Test
    void sourceMustBeApprovedSalesOrderLineWithSameItem() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "0", "3");
        CompanyItem otherItem = createActiveItem(UnitOfMeasure.PCS);

        performCreatePo(purchasing, poBody(supplier, poLine(otherItem, order.salesOrderLineId(), "1", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_SOURCE_INVALID"));

        //同一筆 SO 明細在同一張 PO 只能出現一次
        performCreatePo(purchasing, poBody(supplier, String.join(",",
                        poLine(order.item(), order.salesOrderLineId(), "1", null),
                        poLine(order.item(), order.salesOrderLineId(), "1", null)
                )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PO_LINE_INVALID"));
    }

    @Test
    void inactiveSupplierCannotBeUsed() throws Exception {
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        Supplier supplier = createActiveSupplier();
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);

        supplier.deactivate();
        supplierRepository.saveAndFlush(supplier);

        performCreatePo(purchasing, poBody(supplier, poLine(item, null, "1", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_SUPPLIER_INACTIVE"));
    }

    @Test
    void warehouseCannotSeePurchaseOrders() throws Exception {
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));

        //倉管沒有 PO_VIEW，看不到 PO 單價
        mockMvc.perform(get("/api/v1/purchase-orders").header("Authorization", bearer(warehouse)))
                .andExpect(status().isForbidden());
    }
}
