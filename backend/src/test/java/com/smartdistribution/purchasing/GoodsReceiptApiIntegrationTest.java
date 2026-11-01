package com.smartdistribution.purchasing;

import com.smartdistribution.masterdata.supplier.entity.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收貨入庫：合格數量入庫、補足來源 SO 的預留、PO 狀態推進與收貨限制
 * 每個情境的 SO 都是期初庫存 2、訂購 5：預留 2、缺 3，再用一張 3 個的 PO 補
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoodsReceiptApiIntegrationTest extends PurchaseOrderTestSupport {

    @Test
    void receivingLinkedPoTopsUpReservationAndClosesPo() throws Exception {
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

        postReceipt(warehouseManager, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "3", "3", "0", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber", startsWith("GR-")))
                .andExpect(jsonPath("$.lines[0].acceptedBaseQty").value(3));

        assertThat(onHandQty(order.item())).isEqualByComparingTo("5");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("5");

        getPo(purchasing, purchaseOrderId)
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.lines[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.lines[0].openBaseQty").value(0))
                .andExpect(jsonPath("$.receipts[0].receiptNumber", startsWith("GR-")));

        //到貨後不再是採購在途，SO 不再缺料
        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.inTransitQty").value(0))
                .andExpect(jsonPath("$.lines[0].supply.unplannedQty").value(0));

        //已結案的 PO 不能再收
        postReceipt(warehouseManager, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "1", "1", "0", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_STATUS_CONFLICT"));

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void damagedQuantityIsNotStockedAndLineStaysOpen() throws Exception {
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
        Long poLineId = findPoLineId(purchaseOrderId);

        //有損壞卻沒填原因
        postReceipt(warehouseManager, purchaseOrderId, receiptLine(poLineId, "3", "2", "1", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GOODS_RECEIPT_QUANTITY_INVALID"));

        //合格 ＋ 損壞不等於實收
        postReceipt(warehouseManager, purchaseOrderId, receiptLine(poLineId, "3", "1", "1", "外箱破損"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GOODS_RECEIPT_QUANTITY_INVALID"));

        postReceipt(warehouseManager, purchaseOrderId, receiptLine(poLineId, "3", "2", "1", "外箱破損"))
                .andExpect(status().isCreated());

        //只有合格的 2 入庫並補到預留
        assertThat(onHandQty(order.item())).isEqualByComparingTo("4");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("4");

        getPo(purchasing, purchaseOrderId)
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.lines[0].acceptedBaseQty").value(2))
                .andExpect(jsonPath("$.lines[0].openBaseQty").value(1));

        getOrder(salesManager, order.salesOrderId())
                .andExpect(jsonPath("$.lines[0].supply.inTransitQty").value(1));

        //供應商補寄 1 個後收齊
        postReceipt(warehouseManager, purchaseOrderId, receiptLine(poLineId, "1", "1", "0", null))
                .andExpect(status().isCreated());

        getPo(purchasing, purchaseOrderId).andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(reservedQty(order.item())).isEqualByComparingTo("5");

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void generalReplenishmentDoesNotTopUpShortSalesOrder() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        Long purchaseOrderId = createApprovedPo(
                purchasing, purchasingManager,
                poBody(supplier, poLine(order.item(), null, "3", "120"))
        );

        postReceipt(warehouseManager, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "3", "3", "0", null))
                .andExpect(status().isCreated());

        //一般補庫只增加在庫，SO 的預留不變，缺料仍由業務或採購處理
        assertThat(onHandQty(order.item())).isEqualByComparingTo("5");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("2");

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void reducedDemandOnlyTopsUpWhatIsStillNeeded() throws Exception {
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

        //PO 核准後客戶取消 2 個：剩餘需求 3、預留 2，只缺 1
        Long cancellationId = createSubmittedCancellation(
                order.salesToken(),
                order.salesOrderId(),
                cancellationLine(order.salesOrderLineId(), "2")
        );
        approveCancellation(salesManager, cancellationId).andExpect(status().isOk());

        postReceipt(warehouseManager, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "3", "3", "0", null))
                .andExpect(status().isCreated());

        //補 1 個到預留，多的 2 個成為一般庫存
        assertThat(onHandQty(order.item())).isEqualByComparingTo("5");
        assertThat(reservedQty(order.item())).isEqualByComparingTo("3");

        BigDecimal toppedUp = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservation_event
                WHERE source_type = 'PO_RECEIPT' AND event_type = 'RESERVE'
                  AND reservation_id IN (SELECT id FROM inventory_reservation WHERE sales_order_line_id = ?)
                """,
                BigDecimal.class,
                order.salesOrderLineId()
        );
        assertThat(toppedUp).isEqualByComparingTo("1");

        assertConsistent(warehouseManager, order.item());
    }

    @Test
    void receivingMoreThanOpenQuantityIsRejected() throws Exception {
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
        Long poLineId = findPoLineId(purchaseOrderId);

        postReceipt(warehouseManager, purchaseOrderId, receiptLine(poLineId, "4", "4", "0", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOODS_RECEIPT_QUANTITY_EXCEEDED"));

        //同一筆明細出現兩次
        postReceipt(
                warehouseManager,
                purchaseOrderId,
                receiptLine(poLineId, "1", "1", "0", null) + "," + receiptLine(poLineId, "1", "1", "0", null)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GOODS_RECEIPT_LINE_INVALID"));

        //被擋下的收貨不會留下任何資料
        assertThat(onHandQty(order.item())).isEqualByComparingTo("2");
        Integer receiptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM goods_receipt WHERE purchase_order_id = ?", Integer.class, purchaseOrderId
        );
        assertThat(receiptCount).isZero();
    }

    @Test
    void warehouseSeesOpenPurchaseOrdersWithoutPricesAndOnlyWarehouseCanPost() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String purchasing = login(createUser("purchasing", "PURCHASING"));
        String purchasingManager = login(createUser("purchasing-manager", "PURCHASING_MANAGER"));
        Supplier supplier = createActiveSupplier();
        ShortageOrder order = createShortageOrder(warehouseManager, salesManager, "2", "5");

        Long purchaseOrderId = createApprovedPo(
                purchasing, purchasingManager,
                poBody(supplier, poLine(order.item(), order.salesOrderLineId(), "3", "120"))
        );
        String thisOrder = "$.content[?(@.id == " + purchaseOrderId + ")]";

        mockMvc.perform(get("/api/v1/goods-receipts/open-purchase-orders")
                        .header("Authorization", bearer(warehouse))
                        .param("size", "100")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(thisOrder + ".lines[0].openQty").value(3))
                .andExpect(jsonPath(thisOrder + ".lines[0].unitPriceExcludingTax").doesNotExist());

        //倉管看不到 PO 本身（有單價）
        getPo(warehouse, purchaseOrderId).andExpect(status().isForbidden());

        //採購不能替倉庫收貨
        postReceipt(purchasing, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "3", "3", "0", null))
                .andExpect(status().isForbidden());

        postReceipt(warehouse, purchaseOrderId, receiptLine(findPoLineId(purchaseOrderId), "3", "3", "0", null))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/goods-receipts")
                        .header("Authorization", bearer(purchasing))
                        .param("purchaseOrderId", purchaseOrderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].postedByName").value("warehouse"));

        assertConsistent(warehouseManager, order.item());
    }
}
