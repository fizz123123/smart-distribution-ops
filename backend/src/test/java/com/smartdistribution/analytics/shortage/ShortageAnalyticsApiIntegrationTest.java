package com.smartdistribution.analytics.shortage;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.receivable.ReceivableTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 缺貨與採購分析：供應面的風險跟著 PO 的進度變化、庫存面用近 30 天出貨與採購週期、排序與權限
 * 開發資料庫裡有其他資料，所以每個情境都用 companyItemId 鎖定自己建立的料號
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShortageAnalyticsApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void supplyRisksFollowTheLinkedPurchaseOrder() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();

        //庫存 2、訂 5：預留 2，尚待規劃 3
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");
        setDeliveryDate(order.salesOrderLineId(), today().plusDays(10));

        shortages(purchasing, order.item())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(today().toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].stockRisks").isEmpty())
                .andExpect(jsonPath("$.items[0].supplyRisks").value(contains("UNPLANNED_DEMAND")))
                .andExpect(jsonPath("$.items[0].earliestDeliveryDate").value(today().plusDays(10).toString()))
                .andExpect(jsonPath("$.items[0].stock.availableQty").value(0))
                .andExpect(jsonPath("$.items[0].replenishment.unplannedQty").value(3))
                .andExpect(jsonPath("$.items[0].affectedLines[0].salesOrderLineId").value(order.salesOrderLineId()))
                .andExpect(jsonPath("$.items[0].affectedLines[0].customerName").isNotEmpty())
                .andExpect(jsonPath("$.items[0].affectedLines[0].supplyStatus").value("UNPLANNED"))
                //採購、倉管也看得到，所以不能有任何價格
                .andExpect(jsonPath("$..unitPriceExcludingTax").doesNotExist());

        //缺的 3 個排進送審中的 PO：已規劃未下單
        Long purchaseOrderId = createSubmittedPo(
                purchasing,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );
        shortages(purchasing, order.item())
                .andExpect(jsonPath("$.items[0].supplyRisks").value(contains("PO_NOT_APPROVED")))
                .andExpect(jsonPath("$.items[0].replenishment.unplannedQty").value(0))
                .andExpect(jsonPath("$.items[0].replenishment.plannedNotOrderedQty").value(3))
                .andExpect(jsonPath("$.items[0].affectedLines[0].supplyStatus").value("PLANNED_NOT_ORDERED"))
                .andExpect(jsonPath("$.items[0].affectedLines[0].purchaseOrders[0].purchaseOrderId")
                        .value(purchaseOrderId))
                .andExpect(jsonPath("$.items[0].purchaseOrders[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.items[0].purchaseOrders[0].openQty").value(3));

        //核准了，但預計到貨晚於交期
        approvePo(purchasingManager, purchaseOrderId).andExpect(status().isOk());
        setExpectedArrivalDate(purchaseOrderId, today().plusDays(20));
        shortages(purchasing, order.item())
                .andExpect(jsonPath("$.items[0].supplyRisks").value(contains("ARRIVAL_AFTER_DELIVERY")))
                .andExpect(jsonPath("$.items[0].replenishment.inTransitQty").value(3))
                .andExpect(jsonPath("$.items[0].affectedLines[0].supplyStatus").value("PURCHASE_DELAY_RISK"));

        //預計到貨日已經過了還沒到
        setExpectedArrivalDate(purchaseOrderId, today().minusDays(1));
        shortages(purchasing, order.item())
                .andExpect(jsonPath("$.items[0].supplyRisks").value(contains("ARRIVAL_OVERDUE")))
                .andExpect(jsonPath("$.items[0].affectedLines[0].supplyStatus").value("PURCHASE_DELAY_RISK"));

        //交期前會到：準時在途沒有風險，料號不列出
        setExpectedArrivalDate(purchaseOrderId, today().plusDays(5));
        shortages(purchasing, order.item())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void stockRisksUseRecentShipmentsAndLeadTime() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();

        //庫存 30 全部出貨：近 30 天日出貨量 1
        ApprovedOrder order = createApprovedOrder(warehouseManager, salesManager, "30", "30");
        CompanyItem item = order.item();
        Long deliveryOrderId = createDeliveryOrder(
                order.salesToken(), order.salesOrderId(), doLine(order.salesOrderLineId(), "30")
        );
        ready(warehouse, deliveryOrderId, actualLine(findDeliveryOrderLineIds(deliveryOrderId).getFirst(), "30"))
                .andExpect(status().isOk());
        postShipment(warehouse, deliveryOrderId, today()).andExpect(status().isOk());

        //收齊的補庫 PO：核准到收貨 50 天，這一筆才算採購週期
        Long completed = receive(purchasing, purchasingManager, warehouseManager, supplier, item, "5", "5");
        setPurchaseDates(completed, today().minusDays(60), today().minusDays(10));
        //短收結案的不算
        Long closedShort = receive(purchasing, purchasingManager, warehouseManager, supplier, item, "5", "2");
        closePo(purchasingManager, "close-short", closedShort, "供應商缺貨", poVersion(purchasing, closedShort))
                .andExpect(status().isOk());
        setPurchaseDates(closedShort, today().minusDays(3), today().minusDays(2));
        //超過六個月的不算
        Long old = receive(purchasing, purchasingManager, warehouseManager, supplier, item, "5", "5");
        setPurchaseDates(old, today().minusMonths(7).minusDays(1), today().minusMonths(7));

        //收貨後庫存 5 ＋ 2 ＋ 5 ＝ 12；安全庫存 20
        setSafetyStock(item, "20");

        shortages(purchasing, item)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].stockRisks")
                        .value(contains("BELOW_SAFETY_STOCK", "BELOW_REORDER_POINT")))
                .andExpect(jsonPath("$.items[0].supplyRisks").isEmpty())
                .andExpect(jsonPath("$.items[0].affectedLines").isEmpty())
                .andExpect(jsonPath("$.items[0].earliestDeliveryDate").doesNotExist())
                .andExpect(jsonPath("$.items[0].stock.availableQty").value(12))
                .andExpect(jsonPath("$.items[0].stock.safetyStockQty").value(20))
                .andExpect(jsonPath("$.items[0].stock.averageDailyShipment").value(1))
                //12 ÷ 1
                .andExpect(jsonPath("$.items[0].stock.coverageDays").value(12))
                .andExpect(jsonPath("$.items[0].stock.averageLeadTimeDays").value(50))
                //日出貨量 1 × 採購週期 50：可用 12 ＋ 在途 0 低於 50
                .andExpect(jsonPath("$.items[0].stock.reorderPoint").value(50))
                .andExpect(jsonPath("$.items[0].stock.coverageNote").doesNotExist())
                .andExpect(jsonPath("$.items[0].stock.leadTimeNote").doesNotExist());
    }

    @Test
    void itemsAreSortedByEarliestDeliveryDate() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        ShortageOrder later = createShortageOrder(warehouseManager, salesManager, "0", "5");
        ShortageOrder urgent = createShortageOrder(warehouseManager, salesManager, "0", "5");
        setDeliveryDate(later.salesOrderLineId(), today().plusDays(10));
        setDeliveryDate(urgent.salesOrderLineId(), today().plusDays(3));

        //不篩選料號時會包含開發資料庫的其他料號，所以只比這兩個料號的先後
        String body = mockMvc.perform(get("/api/v1/analytics/shortages")
                        .header("Authorization", bearer(salesManager)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> itemIds = JsonPath.read(body, "$.items[*].companyItemId");

        int urgentIndex = itemIds.indexOf(urgent.item().getId().intValue());
        int laterIndex = itemIds.indexOf(later.item().getId().intValue());
        assertThat(urgentIndex).isNotNegative();
        assertThat(laterIndex).isGreaterThan(urgentIndex);
    }

    @Test
    void notesExplainMissingNumbersAndOnlyShortageRolesCanView() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "0", "5");

        //沒有出貨、沒有收齊的 PO：可支撐天數與再訂購點算不出來，附上原因
        shortages(login(createUser("warehouse", "WAREHOUSE")), order.item())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stock.coverageDays").doesNotExist())
                .andExpect(jsonPath("$.items[0].stock.coverageNote").value("NO_RECENT_USAGE"))
                .andExpect(jsonPath("$.items[0].stock.averageLeadTimeDays").doesNotExist())
                .andExpect(jsonPath("$.items[0].stock.leadTimeNote").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.items[0].stock.reorderPoint").doesNotExist());

        shortages(salesManager, order.item()).andExpect(status().isOk());
        shortages(login(createUser("manager", "MANAGER")), order.item()).andExpect(status().isOk());
        shortages(login(createUser("finance", "FINANCE")), order.item()).andExpect(status().isForbidden());
        shortages(order.salesToken(), order.item()).andExpect(status().isForbidden());
    }

    private ResultActions shortages(String token, CompanyItem item) throws Exception {
        return mockMvc.perform(get("/api/v1/analytics/shortages")
                .header("Authorization", bearer(token))
                .param("companyItemId", item.getId().toString()));
    }

    //補庫 PO（不關聯 SO）：核准後收貨，回傳 PO ID
    private Long receive(
            String purchasing,
            String purchasingManager,
            String warehouseManager,
            Supplier supplier,
            CompanyItem item,
            String ordered,
            String accepted
    ) throws Exception {
        Long purchaseOrderId = createApprovedPo(
                purchasing, purchasingManager, poBody(supplier, poLine(item, null, ordered, "100"))
        );
        postReceipt(
                warehouseManager,
                purchaseOrderId,
                receiptLine(findPoLineId(purchaseOrderId), accepted, accepted, "0", null)
        ).andExpect(status().isCreated());

        return purchaseOrderId;
    }

    //核准日與收貨日 API 設定不到，直接改
    private void setPurchaseDates(Long purchaseOrderId, LocalDate approvedDate, LocalDate receivedDate) {
        jdbcTemplate.update(
                "UPDATE purchase_order SET approved_at = ? WHERE id = ?",
                approvedDate.atTime(12, 0),
                purchaseOrderId
        );
        jdbcTemplate.update(
                "UPDATE goods_receipt SET received_date = ? WHERE purchase_order_id = ?",
                receivedDate,
                purchaseOrderId
        );
    }

    private void setExpectedArrivalDate(Long purchaseOrderId, LocalDate expectedArrivalDate) {
        jdbcTemplate.update(
                "UPDATE purchase_order SET expected_arrival_date = ? WHERE id = ?",
                expectedArrivalDate,
                purchaseOrderId
        );
    }

    private void setDeliveryDate(Long salesOrderLineId, LocalDate deliveryDate) {
        jdbcTemplate.update(
                "UPDATE sales_order_line SET delivery_date = ? WHERE id = ?",
                deliveryDate,
                salesOrderLineId
        );
    }

    private void setSafetyStock(CompanyItem item, String quantity) {
        jdbcTemplate.update(
                "UPDATE inventory_policy SET safety_stock_base_qty = ? WHERE company_item_id = ?",
                quantity,
                item.getId()
        );
    }
}
