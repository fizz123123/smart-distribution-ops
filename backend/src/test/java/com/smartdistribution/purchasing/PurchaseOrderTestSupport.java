package com.smartdistribution.purchasing;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.masterdata.supplier.repository.SupplierRepository;
import com.smartdistribution.sales.SalesOrderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PO 整合測試共用的資料準備與清理；客戶、料號、SO 與帳號沿用 SO 測試的工具
 * 供應商直接透過 entity 建立並轉為啟用，不經過核准流程
 */
abstract class PurchaseOrderTestSupport extends SalesOrderTestSupport {

    static final String SUPPLIER_NAME_PREFIX = "整合測試供應商-PO-";

    @Autowired
    SupplierRepository supplierRepository;

    //SO 測試的清理（父類別）在之前執行；兩邊沒有外鍵，順序不影響
    @BeforeEach
    @AfterEach
    void cleanPurchaseOrders() {
        List<Long> purchaseOrderIds = jdbcTemplate.queryForList(
                """
                SELECT po.id FROM purchase_order po
                JOIN supplier supplier ON supplier.id = po.supplier_id
                WHERE supplier.name LIKE ?
                """,
                Long.class,
                SUPPLIER_NAME_PREFIX + "%"
        );

        for (Long purchaseOrderId : purchaseOrderIds) {
            jdbcTemplate.update(
                    "DELETE FROM document_file WHERE document_type = 'PURCHASE_ORDER' AND source_id = ?",
                    purchaseOrderId
            );
            jdbcTemplate.update(
                    """
                    DELETE FROM approval_action
                    WHERE approval_request_id IN (
                        SELECT id FROM approval_request
                        WHERE target_type = 'PURCHASE_ORDER' AND target_id = ?
                    )
                    """,
                    purchaseOrderId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_request WHERE target_type = 'PURCHASE_ORDER' AND target_id = ?",
                    purchaseOrderId
            );
            jdbcTemplate.update(
                    """
                    DELETE line FROM goods_receipt_line line
                    JOIN goods_receipt receipt ON receipt.id = line.goods_receipt_id
                    WHERE receipt.purchase_order_id = ?
                    """,
                    purchaseOrderId
            );
            jdbcTemplate.update("DELETE FROM goods_receipt WHERE purchase_order_id = ?", purchaseOrderId);
            jdbcTemplate.update("DELETE FROM purchase_order_line WHERE purchase_order_id = ?", purchaseOrderId);
            jdbcTemplate.update("DELETE FROM purchase_order WHERE id = ?", purchaseOrderId);
        }

        jdbcTemplate.update("DELETE FROM supplier WHERE name LIKE ?", SUPPLIER_NAME_PREFIX + "%");
    }

    // ===== 主檔與 SO =====

    Supplier createActiveSupplier() {
        Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
                SUPPLIER_NAME_PREFIX + UUID.randomUUID().toString().substring(0, 8),
                null,
                "陳先生",
                "04-22000000",
                null,
                "台中市南屯區工業區二路 2 號",
                1L
        ));
        supplier.assignCode();
        supplier.submit();
        supplier.approve();

        return supplierRepository.saveAndFlush(supplier);
    }

    /**
     * 建立一張已核准、會缺料的 SO：料號期初庫存 available 個，訂購 ordered 個
     * 例如 available 2、ordered 5 → 預留 2、尚待規劃 3
     */
    ShortageOrder createShortageOrder(String warehouseManager, String salesManager, String available, String ordered)
            throws Exception {
        String sales = login(createUser("po-sales", "SALES"));
        Customer customer = createActiveCustomer(PaymentTerm.DUE_0);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        createActivePrice(mapping, UnitOfMeasure.PCS, "200");

        if (Integer.parseInt(available) > 0) {
            postOpeningBalance(warehouseManager, item, available);
        }

        Long salesOrderId = createSubmittedOrder(sales, draftBody(customer, line(mapping, ordered, UnitOfMeasure.PCS)));
        approve(salesManager, salesOrderId).andExpect(status().isOk());

        return new ShortageOrder(salesOrderId, findLineIds(salesOrderId).getFirst(), item, sales);
    }

    // ===== PO =====

    String poLine(CompanyItem item, Long salesOrderLineId, String quantity, String unitPrice) {
        return """
                {"companyItemId": %d, "salesOrderLineId": %s, "quantity": %s, "unit": "PCS", "unitPriceExcludingTax": %s}
                """.formatted(item.getId(), salesOrderLineId, quantity, unitPrice);
    }

    String poBody(Supplier supplier, String lines) {
        return """
                {
                  "supplierId": %d,
                  "expectedArrivalDate": "2026-11-10",
                  "lines": [%s]
                }
                """.formatted(supplier.getId(), lines);
    }

    ResultActions performCreatePo(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/purchase-orders")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    PurchaseOrderVersion createPoDraft(String token, String body) throws Exception {
        MvcResult result = performCreatePo(token, body)
                .andExpect(status().isCreated())
                .andReturn();

        return new PurchaseOrderVersion(readLong(result, "$.id"), readLong(result, "$.version"));
    }

    ResultActions updatePo(String token, PurchaseOrderVersion purchaseOrder, Supplier supplier, String lines)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/purchase-orders/{id}", purchaseOrder.id())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "supplierId": %d,
                          "expectedArrivalDate": "2026-11-10",
                          "lines": [%s],
                          "version": %d
                        }
                        """.formatted(supplier.getId(), lines, purchaseOrder.version())));
    }

    ResultActions submitPo(String token, PurchaseOrderVersion purchaseOrder) throws Exception {
        return mockMvc.perform(post("/api/v1/purchase-orders/{id}/submit", purchaseOrder.id())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d}
                        """.formatted(purchaseOrder.version())));
    }

    //建立草稿並送審，回傳 PO ID
    Long createSubmittedPo(String token, String body) throws Exception {
        PurchaseOrderVersion draft = createPoDraft(token, body);
        submitPo(token, draft).andExpect(status().isOk());

        return draft.id();
    }

    Long findPendingPoApprovalId(Long purchaseOrderId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM approval_request
                WHERE target_type = 'PURCHASE_ORDER' AND target_id = ? AND status = 'PENDING'
                """,
                Long.class,
                purchaseOrderId
        );
    }

    ResultActions approvePo(String token, Long purchaseOrderId) throws Exception {
        return mockMvc.perform(post("/api/v1/approval-requests/{id}/approve", findPendingPoApprovalId(purchaseOrderId))
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    ResultActions getPo(String token, Long purchaseOrderId) throws Exception {
        return mockMvc.perform(get("/api/v1/purchase-orders/{id}", purchaseOrderId)
                .header("Authorization", bearer(token)));
    }

    //建立、送審並核准，回傳 PO ID
    Long createApprovedPo(String purchasing, String purchasingManager, String body) throws Exception {
        Long purchaseOrderId = createSubmittedPo(purchasing, body);
        approvePo(purchasingManager, purchaseOrderId).andExpect(status().isOk());

        return purchaseOrderId;
    }

    Long findPoLineId(Long purchaseOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM purchase_order_line WHERE purchase_order_id = ? ORDER BY line_no LIMIT 1",
                Long.class,
                purchaseOrderId
        );
    }

    Long poVersion(String token, Long purchaseOrderId) throws Exception {
        return readLong(getPo(token, purchaseOrderId).andExpect(status().isOk()).andReturn(), "$.version");
    }

    //action 是 cancel 或 close-short，兩者的 request 相同
    ResultActions closePo(String token, String action, Long purchaseOrderId, String reason, Long version)
            throws Exception {
        return mockMvc.perform(post("/api/v1/purchase-orders/{id}/" + action, purchaseOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s", "version": %d}
                        """.formatted(reason, version)));
    }

    // ===== 收貨 =====

    String receiptLine(Long purchaseOrderLineId, String received, String accepted, String damaged, String reason) {
        return """
                {"purchaseOrderLineId": %d, "receivedQuantity": %s, "acceptedQuantity": %s,
                 "damagedQuantity": %s, "nonconformingQuantity": 0, "exceptionReason": %s}
                """.formatted(
                purchaseOrderLineId, received, accepted, damaged, reason == null ? "null" : "\"" + reason + "\""
        );
    }

    ResultActions postReceipt(String token, Long purchaseOrderId, String lines) throws Exception {
        return mockMvc.perform(post("/api/v1/purchase-orders/{id}/goods-receipts", purchaseOrderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receivedDate": "2026-11-05", "lines": [%s]}
                        """.formatted(lines)));
    }

    record PurchaseOrderVersion(Long id, Long version) {
    }

    /**
     * @param salesOrderLineId 會缺料的那一筆 SO 明細
     * @param salesToken       建立這張 SO 的業務，之後申請餘量取消會用到
     */
    record ShortageOrder(Long salesOrderId, Long salesOrderLineId, CompanyItem item, String salesToken) {
    }
}
