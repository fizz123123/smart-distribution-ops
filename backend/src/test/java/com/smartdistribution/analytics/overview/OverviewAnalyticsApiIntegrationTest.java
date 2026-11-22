package com.smartdistribution.analytics.overview;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.receivable.ReceivableTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 營運總覽：每個數字都和對應的詳情分析相同，清單最多 10 筆並附總筆數；只有營運主管能看
 * 缺貨分析沒有客戶篩選，會包含開發資料庫的其他料號，所以只比對總覽和詳情一致，不寫死筆數
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OverviewAnalyticsApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void overviewNumbersMatchTheDetailAnalyses() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
        String manager = login(createUser("manager", "MANAGER"));

        //40 天前出貨一次（月結 30 天，已經逾期 10 天）、2 天前再出貨一次（本期營收 500）
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);
        shipAndPost(order, warehouse, "4", "1", today().minusDays(40));
        shipAndPost(order, warehouse, "3", "1", today().minusDays(2));
        String customerId = order.customer().getId().toString();

        //期間自己帶（今天往前 9 天到今天），避免月初跑測試時 2 天前落在上個月
        String overview = read(manager, withPeriod(get("/api/v1/analytics/overview")).param("customerId", customerId));
        String sales = read(manager, withPeriod(get("/api/v1/analytics/sales")).param("customerId", customerId));
        String shortages = read(manager, get("/api/v1/analytics/shortages"));
        String cooling = read(
                manager, withPeriod(get("/api/v1/analytics/customer-cooling")).param("customerId", customerId)
        );
        String receivables = read(manager, get("/api/v1/analytics/receivables").param("customerId", customerId));

        //有資料才比得出意義：本期營收 500、逾期應收 1 筆
        assertThat(number(overview, "$.cards.revenue.current")).isEqualTo(500);
        assertThat(number(overview, "$.cards.overdueReceivables.count")).isEqualTo(1);

        //卡片 1～4、趨勢：取自接單營收分析
        assertSame(overview, "$.period", sales, "$.period");
        assertSame(overview, "$.cards.revenue", sales, "$.comparison.revenue");
        assertSame(overview, "$.cards.netOrderAmount", sales, "$.comparison.netOrderAmount");
        assertSame(overview, "$.cards.backlog.amount", sales, "$.backlog.amount");
        assertSame(overview, "$.cards.backlog.overdueAmount", sales, "$.backlog.overdueAmount");
        assertSame(overview, "$.cards.shortageImpact.amount", sales, "$.backlog.shortageImpactAmount");
        assertSame(overview, "$.cards.shortageImpact.itemCount", sales, "$.backlog.shortageImpactItemCount");
        assertSame(overview, "$.trend", sales, "$.trend");

        //卡片 5：交易降溫的 HIGH ＋ MEDIUM
        List<String> levels = JsonPath.read(cooling, "$.customers[*].level");
        long warned = levels.stream().filter(level -> "HIGH".equals(level) || "MEDIUM".equals(level)).count();
        assertThat(number(overview, "$.cards.cooling.customerCount")).isEqualTo(warned);

        //卡片 6：應收的逾期分組
        assertSame(
                overview, "$.cards.overdueReceivables.outstandingAmount",
                receivables, "$.summary.overdue.outstandingAmount"
        );
        assertSame(
                overview, "$.cards.overdueReceivables.unconfirmedCount",
                receivables, "$.summary.overdue.unconfirmedCount"
        );

        //待處理清單：總筆數和詳情相同，最多 10 筆，順序沿用詳情
        assertThat(number(overview, "$.pending.shortages.totalCount"))
                .isEqualTo(number(shortages, "$.items.length()"));
        assertThat(number(overview, "$.pending.shortages.items.length()")).isLessThanOrEqualTo(10);
        if (number(shortages, "$.items.length()") > 0) {
            assertSame(overview, "$.pending.shortages.items[0].companyItemId", shortages, "$.items[0].companyItemId");
        }
        assertThat(number(overview, "$.pending.dueReceivables.totalCount"))
                .isEqualTo(number(receivables, "$.dueReceivables.length()"));
        assertSame(overview, "$.pending.dueReceivables.items[0]", receivables, "$.dueReceivables[0]");
    }

    @Test
    void onlyManagerCanViewOverview() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", bearer(login(createUser("manager", "MANAGER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.coolingCustomers.items.length()").isNumber());

        for (String role : List.of("SALES_MANAGER", "SALES", "PURCHASING", "WAREHOUSE", "FINANCE")) {
            mockMvc.perform(get("/api/v1/analytics/overview")
                            .header("Authorization", bearer(login(createUser("viewer", role)))))
                    .andExpect(status().isForbidden());
        }
    }

    private static MockHttpServletRequestBuilder withPeriod(MockHttpServletRequestBuilder request) {
        return request
                .param("from", today().minusDays(9).toString())
                .param("to", today().toString());
    }

    private String read(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static int number(String body, String path) {
        Number value = JsonPath.read(body, path);
        return value.intValue();
    }

    //兩邊讀出來的 JSON 片段要完全相同
    private static void assertSame(String overview, String overviewPath, String detail, String detailPath) {
        Object expected = JsonPath.read(detail, detailPath);
        Object actual = JsonPath.read(overview, overviewPath);
        assertThat(actual).as(overviewPath).isEqualTo(expected);
    }
}
