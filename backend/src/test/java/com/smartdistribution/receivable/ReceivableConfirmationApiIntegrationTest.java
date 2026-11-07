package com.smartdistribution.receivable;

import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 確認正式應收、更正外部發票、即將到期與逾期（AT-052、AT-057、AT-058）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceivableConfirmationApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void financeConfirmsDraftWithValidUniqueInvoice() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        String manager = login(createUser("manager", "MANAGER"));
        Long first = findReceivableId(shipAndPost(
                createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30), warehouse, "10", "5", today()
        ));
        Long second = findReceivableId(shipAndPost(
                createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30), warehouse, "10", "5", today()
        ));
        String invoiceNumber = randomInvoiceNumber();

        //格式不符、日期在未來都被擋下
        confirm(finance, first, "AB1234", today())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIVABLE_INVOICE_INVALID"));
        confirm(finance, first, invoiceNumber, today().plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIVABLE_INVOICE_INVALID"));

        //營運主管只能看，不能確認
        confirm(manager, first, invoiceNumber, today(), receivableVersion(finance, first))
                .andExpect(status().isForbidden());

        //小寫會轉成大寫；轉為正式應收，記下誰確認的
        confirm(finance, first, invoiceNumber.toLowerCase(), today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.externalInvoice.number").value(invoiceNumber))
                .andExpect(jsonPath("$.externalInvoice.date").value(today().toString()))
                .andExpect(jsonPath("$.confirmation.at").isNotEmpty())
                .andExpect(jsonPath("$.totalAmount").value(2100));

        //不能確認兩次；另一筆應收不能用同一個發票號碼
        confirm(finance, first, randomInvoiceNumber(), today())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECEIVABLE_STATUS_CONFLICT"));
        confirm(finance, second, invoiceNumber, today())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECEIVABLE_INVOICE_NUMBER_DUPLICATE"));

        //版本不對
        confirm(finance, second, randomInvoiceNumber(), today(), receivableVersion(finance, second) + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void financeManagerCorrectsOnlyInvoiceAndLeavesAuditTrail() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        String financeManager = login(createUser("finance-manager", "FINANCE_MANAGER"));
        Long receivableId = findReceivableId(shipAndPost(
                createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30), warehouse, "10", "5", today()
        ));
        String originalNumber = randomInvoiceNumber();
        String correctedNumber = randomInvoiceNumber();

        //草稿還沒有發票，不能更正
        correctExternalInvoice(financeManager, receivableId, correctionBody(correctedNumber, "輸入錯誤", receivableVersion(finance, receivableId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECEIVABLE_STATUS_CONFLICT"));

        confirm(finance, receivableId, originalNumber, today().minusDays(1)).andExpect(status().isOk());
        Long version = receivableVersion(finance, receivableId);

        //財務不能更正；原因必填
        correctExternalInvoice(finance, receivableId, correctionBody(correctedNumber, "輸入錯誤", version))
                .andExpect(status().isForbidden());
        correctExternalInvoice(financeManager, receivableId, correctionBody(correctedNumber, "", version))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        //順便送了金額、到期日、DO，都不會生效
        correctExternalInvoice(financeManager, receivableId, """
                {
                  "externalInvoiceNumber": "%s",
                  "externalInvoiceDate": "%s",
                  "reason": "原發票號碼輸入錯誤",
                  "version": %d,
                  "totalAmount": 1,
                  "dueDate": "2000-01-01",
                  "paymentTerm": "DUE_0",
                  "deliveryOrderId": 1
                }
                """.formatted(correctedNumber, today(), version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.externalInvoice.number").value(correctedNumber))
                .andExpect(jsonPath("$.externalInvoice.date").value(today().toString()))
                .andExpect(jsonPath("$.totalAmount").value(2100))
                .andExpect(jsonPath("$.paymentTerm").value("NET_30"))
                .andExpect(jsonPath("$.dueDate").value(today().plusDays(30).toString()));

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                SELECT before_data, after_data, reason
                FROM audit_log
                WHERE target_type = 'RECEIVABLE' AND target_id = ? AND action_type = 'RECEIVABLE_INVOICE_CORRECT'
                """,
                receivableId
        );
        assertThat((String) audit.get("before_data")).contains(originalNumber, today().minusDays(1).toString());
        assertThat((String) audit.get("after_data")).contains(correctedNumber, today().toString());
        assertThat(audit.get("reason")).isEqualTo("原發票號碼輸入錯誤");
    }

    @Test
    void dueSoonIncludesTodayToTheSeventhDayAndOverdueIsBeforeToday() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));

        //DUE_0 的到期日就是出貨日；NET_30 從出貨日往後 30 天
        Long overdue = receivableDueOn(today().minusDays(1), PaymentTerm.DUE_0, warehouseManager, salesManager, warehouse);
        Long dueToday = receivableDueOn(today(), PaymentTerm.DUE_0, warehouseManager, salesManager, warehouse);
        Long dueOnDaySeven = receivableDueOn(today().minusDays(23), PaymentTerm.NET_30, warehouseManager, salesManager, warehouse);
        Long dueOnDayEight = receivableDueOn(today().minusDays(22), PaymentTerm.NET_30, warehouseManager, salesManager, warehouse);

        getReceivable(finance, overdue).andExpect(jsonPath("$.dueStatus").value("OVERDUE"));
        getReceivable(finance, dueToday).andExpect(jsonPath("$.dueStatus").value("DUE_SOON"));
        getReceivable(finance, dueOnDaySeven).andExpect(jsonPath("$.dueStatus").value("DUE_SOON"));
        getReceivable(finance, dueOnDayEight).andExpect(jsonPath("$.dueStatus").value(nullValue()));

        //未確認的草稿也算：客戶已經欠款，財務還沒確認本身就要被看到
        listByDueStatus(finance, "OVERDUE", overdue).andExpect(jsonPath("$.content.length()").value(1));
        listByDueStatus(finance, "OVERDUE", dueToday).andExpect(jsonPath("$.content.length()").value(0));
        listByDueStatus(finance, "DUE_SOON", dueToday).andExpect(jsonPath("$.content.length()").value(1));
        listByDueStatus(finance, "DUE_SOON", dueOnDaySeven).andExpect(jsonPath("$.content.length()").value(1));
        listByDueStatus(finance, "DUE_SOON", dueOnDayEight).andExpect(jsonPath("$.content.length()").value(0));
        listByDueStatus(finance, "DUE_SOON", overdue)
                .andExpect(jsonPath("$.content.length()").value(0));

        //清單上也帶著到期狀態與應收狀態，分得出是不是草稿
        mockMvc.perform(get("/api/v1/receivables")
                        .param("keyword", receivableNumber(overdue))
                        .header("Authorization", bearer(finance)))
                .andExpect(jsonPath("$.content[0].dueStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.content[0].receivableNumber", containsString("AR-")));
    }

    private Long receivableDueOn(
            LocalDate shippedOn,
            PaymentTerm paymentTerm,
            String warehouseManager,
            String salesManager,
            String warehouse
    ) throws Exception {
        MainOrder order = createMainOrder(warehouseManager, salesManager, paymentTerm);
        return findReceivableId(shipAndPost(order, warehouse, "10", "5", shippedOn));
    }

    //用應收編號鎖定這一筆，避免開發資料庫裡的其他應收影響結果
    private ResultActions listByDueStatus(String token, String dueStatus, Long receivableId) throws Exception {
        return mockMvc.perform(get("/api/v1/receivables")
                        .param("dueStatus", dueStatus)
                        .param("keyword", receivableNumber(receivableId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private static String correctionBody(String invoiceNumber, String reason, Long version) {
        return """
                {"externalInvoiceNumber": "%s", "externalInvoiceDate": "%s", "reason": "%s", "version": %d}
                """.formatted(invoiceNumber, today(), reason, version);
    }
}
