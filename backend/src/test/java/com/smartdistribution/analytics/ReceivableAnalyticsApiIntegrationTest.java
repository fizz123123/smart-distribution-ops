package com.smartdistribution.analytics;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.receivable.ReceivableTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 應收分析：正式應收與草稿分開算、作廢的收款不扣、帳齡分組，以及和應收清單的逾期篩選一致
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceivableAnalyticsApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void summaryAgingAndDueListFollowConfirmedPaymentsAndIncludeDrafts() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String finance = login(createUser("finance", "FINANCE"));
        String financeManager = login(createUser("finance-manager", "FINANCE_MANAGER"));

        //SO_MAIN 分三次出貨，付款條件 DUE_0：到期日就是出貨日
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.DUE_0);
        Long customerId = order.customer().getId();
        //PE 4 × 100 ＋ PU 2 × 200 ＝ 800，含稅 840；逾期 45 天
        Long overdue45 = findReceivableId(shipAndPost(order, warehouse, "4", "2", today().minusDays(45)));
        //PE 3 × 100 ＋ PU 1 × 200 ＝ 500，含稅 525；逾期 1 天
        Long overdue1 = findReceivableId(shipAndPost(order, warehouse, "3", "1", today().minusDays(1)));
        //PE 3 × 100 ＋ PU 2 × 200 ＝ 700，含稅 735；今天到期，留著不確認
        Long dueToday = findReceivableId(shipAndPost(order, warehouse, "3", "2", today()));

        confirmReceivable(finance, overdue45);
        confirmReceivable(finance, overdue1);
        payToday(finance, customerId, "340", allocation(overdue45, "340"));
        //作廢的收款不扣未收
        Long voided = payToday(finance, customerId, "200", allocation(overdue1, "200"));
        voidPayment(financeManager, voided, "金額登錯", readLong(getPayment(finance, voided).andReturn(), "$.version"))
                .andExpect(status().isOk());

        analysis(finance, customerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(today().toString()))
                //正式應收：840 ＋ 525；未收：500 ＋ 525
                .andExpect(jsonPath("$.summary.totalAmount").value(1365))
                .andExpect(jsonPath("$.summary.outstandingAmount").value(1025))
                .andExpect(jsonPath("$.summary.pendingConfirmationCount").value(1))
                .andExpect(jsonPath("$.summary.pendingConfirmationAmount").value(735))
                //即將到期與逾期包含草稿
                .andExpect(jsonPath("$.summary.dueSoon.count").value(1))
                .andExpect(jsonPath("$.summary.dueSoon.outstandingAmount").value(735))
                .andExpect(jsonPath("$.summary.dueSoon.unconfirmedCount").value(1))
                .andExpect(jsonPath("$.summary.overdue.count").value(2))
                .andExpect(jsonPath("$.summary.overdue.outstandingAmount").value(1025))
                .andExpect(jsonPath("$.summary.overdue.unconfirmedCount").value(0))
                //帳齡：每一組都列出
                .andExpect(jsonPath("$.aging.length()").value(5))
                .andExpect(jsonPath("$.aging[0].bucket").value("NOT_DUE"))
                .andExpect(jsonPath("$.aging[0].outstandingAmount").value(735))
                .andExpect(jsonPath("$.aging[1].bucket").value("DAYS_1_30"))
                .andExpect(jsonPath("$.aging[1].outstandingAmount").value(525))
                .andExpect(jsonPath("$.aging[2].bucket").value("DAYS_31_60"))
                .andExpect(jsonPath("$.aging[2].outstandingAmount").value(500))
                .andExpect(jsonPath("$.aging[3].count").value(0))
                .andExpect(jsonPath("$.aging[4].count").value(0))
                //明細依到期日排序
                .andExpect(jsonPath("$.dueReceivables.length()").value(3))
                .andExpect(jsonPath("$.dueReceivables[0].receivableId").value(overdue45))
                .andExpect(jsonPath("$.dueReceivables[0].daysPastDue").value(45))
                .andExpect(jsonPath("$.dueReceivables[1].receivableId").value(overdue1))
                .andExpect(jsonPath("$.dueReceivables[2].receivableId").value(dueToday))
                .andExpect(jsonPath("$.dueReceivables[2].dueStatus").value("DUE_SOON"))
                .andExpect(jsonPath("$.dueReceivables[2].status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.dueReceivables[2].daysPastDue").value(0));

        //儀表板的逾期合計和應收清單「逾期」篩選的未收合計相同
        MvcResult overdueList = mockMvc.perform(get("/api/v1/receivables")
                        .header("Authorization", bearer(finance))
                        .param("customerId", customerId.toString())
                        .param("dueStatus", "OVERDUE"))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> balances = JsonPath.read(overdueList.getResponse().getContentAsString(), "$.content[*].balance");
        BigDecimal listTotal = balances.stream()
                .map(balance -> new BigDecimal(balance.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(listTotal).isEqualByComparingTo("1025");

        //依負責業務篩選：這張 SO 的負責業務看得到，其他業務的 ID 查不到
        Long responsibleSalesUserId = jdbcTemplate.queryForObject(
                "SELECT responsible_sales_user_id FROM sales_order WHERE id = ?",
                Long.class,
                order.salesOrderId()
        );
        mockMvc.perform(get("/api/v1/analytics/receivables")
                        .header("Authorization", bearer(finance))
                        .param("customerId", customerId.toString())
                        .param("responsibleSalesUserId", responsibleSalesUserId.toString()))
                .andExpect(jsonPath("$.summary.overdue.count").value(2));
        mockMvc.perform(get("/api/v1/analytics/receivables")
                        .header("Authorization", bearer(finance))
                        .param("customerId", customerId.toString())
                        .param("responsibleSalesUserId", String.valueOf(responsibleSalesUserId + 100_000)))
                .andExpect(jsonPath("$.summary.overdue.count").value(0))
                .andExpect(jsonPath("$.summary.totalAmount").value(0));
    }

    @Test
    void onlyFinanceAndManagerCanSeeReceivableAnalysis() throws Exception {
        analysis(login(createUser("finance", "FINANCE")), null).andExpect(status().isOk());
        analysis(login(createUser("finance-manager", "FINANCE_MANAGER")), null).andExpect(status().isOk());
        analysis(login(createUser("manager", "MANAGER")), null).andExpect(status().isOk());

        analysis(login(createUser("sales", "SALES")), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        analysis(login(createUser("sales-manager", "SALES_MANAGER")), null).andExpect(status().isForbidden());
        analysis(login(createUser("warehouse", "WAREHOUSE")), null).andExpect(status().isForbidden());
    }

    private ResultActions analysis(String token, Long customerId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/analytics/receivables")
                .header("Authorization", bearer(token));
        if (customerId != null) {
            request.param("customerId", customerId.toString());
        }
        return mockMvc.perform(request);
    }
}
