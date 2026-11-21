package com.smartdistribution.analytics.sales;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.receivable.ReceivableTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接單與營收分析：營收依實際出貨日、取消依核准日、準時交貨、增減貢獻、已接單未出貨，以及業務只看自己負責的 SO
 * 期間型指標自己帶 from、to（今天往前 9 天到今天），避免月初跑測試時「昨天」落在上個月
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesAnalyticsApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void periodComparisonTrendContributionsAndBacklog() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));

        //SO_MAIN 今天核准：PE 10 × 100 ＋ PU 5 × 200 ＝ 2,000
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);
        //交期：PE 還很久，PU 是昨天，用來驗證準時與逾期未出貨
        setDeliveryDate(order.peLineId(), today().plusDays(30));
        setDeliveryDate(order.puLineId(), today().minusDays(1));

        //前期（12 天前）出貨 PE 4、PU 1：營收 600
        shipAndPost(order, warehouse, "4", "1", today().minusDays(12));
        //本期出貨兩次，各 PE 3、PU 1：營收 500 ＋ 500；今天那次的 PU 晚於交期
        shipAndPost(order, warehouse, "3", "1", today().minusDays(2));
        shipAndPost(order, warehouse, "3", "1", today());
        //PU 還剩 2 個，取消 1 個：取消額 200，剩 1 個未出貨
        Long cancellationId = createSubmittedCancellation(
                order.salesToken(), order.salesOrderId(), cancellationLine(order.puLineId(), "1")
        );
        approveCancellation(salesManager, cancellationId).andExpect(status().isOk());

        analysis(salesManager, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.previousFrom").value(today().minusDays(19).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(today().minusDays(10).toString()))
                //營收：本期 1,000、前期 600
                .andExpect(jsonPath("$.comparison.revenue.current").value(1000))
                .andExpect(jsonPath("$.comparison.revenue.previous").value(600))
                .andExpect(jsonPath("$.comparison.revenue.change").value(400))
                //接單：前期是 0，不算變化率
                .andExpect(jsonPath("$.comparison.newOrderAmount.current").value(2000))
                .andExpect(jsonPath("$.comparison.newOrderAmount.changeRate").doesNotExist())
                .andExpect(jsonPath("$.comparison.newOrderAmount.note").value("NO_PRIOR_BASELINE"))
                .andExpect(jsonPath("$.comparison.cancellationAmount.current").value(200))
                .andExpect(jsonPath("$.comparison.netOrderAmount.current").value(1800))
                .andExpect(jsonPath("$.comparison.newOrderCount.current").value(1))
                .andExpect(jsonPath("$.comparison.averageOrderAmount.current").value(2000))
                .andExpect(jsonPath("$.comparison.activeCustomerCount.current").value(1))
                .andExpect(jsonPath("$.comparison.activeCustomerCount.previous").value(1))
                .andExpect(jsonPath("$.comparison.orderingCustomerCount.current").value(1))
                //準時交貨：本期 4 筆出貨明細，今天的 PU 晚了；前期 2 筆都準時
                .andExpect(jsonPath("$.comparison.onTimeDelivery.current.shippedLineCount").value(4))
                .andExpect(jsonPath("$.comparison.onTimeDelivery.current.onTimeLineCount").value(3))
                .andExpect(jsonPath("$.comparison.onTimeDelivery.current.rate").value(0.75))
                .andExpect(jsonPath("$.comparison.onTimeDelivery.previous.rate").value(1))
                //增減貢獻：客戶 ＋400；PE、PU 都是 ＋200
                .andExpect(jsonPath("$.contributions.customerIncreases.length()").value(1))
                .andExpect(jsonPath("$.contributions.customerIncreases[0].id").value(order.customer().getId()))
                .andExpect(jsonPath("$.contributions.customerIncreases[0].change").value(400))
                .andExpect(jsonPath("$.contributions.customerDecreases.length()").value(0))
                .andExpect(jsonPath("$.contributions.itemIncreases[*].id").value(containsInAnyOrder(
                        order.pe().getId().intValue(), order.pu().getId().intValue()
                )))
                .andExpect(jsonPath("$.contributions.itemIncreases[0].change").value(200))
                //已接單未出貨：PU 剩 1 個，交期是昨天，所以也是逾期未出貨
                .andExpect(jsonPath("$.backlog.amount").value(200))
                .andExpect(jsonPath("$.backlog.lineCount").value(1))
                .andExpect(jsonPath("$.backlog.overdueAmount").value(200))
                .andExpect(jsonPath("$.backlog.overdueLineCount").value(1))
                //近六個月：最後一個月是本月，標示截至今天
                .andExpect(jsonPath("$.trend.length()").value(6))
                .andExpect(jsonPath("$.trend[5].partial").value(true))
                .andExpect(jsonPath("$.trend[4].partial").value(false));

        //趨勢不受期間影響：六個月加起來包含前期與本期的全部資料
        MvcResult result = analysis(salesManager, order).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(sum(body, "$.trend[*].revenue")).isEqualByComparingTo("1600");
        assertThat(sum(body, "$.trend[*].newOrderAmount")).isEqualByComparingTo("2000");
        assertThat(sum(body, "$.trend[*].cancellationAmount")).isEqualByComparingTo("200");
    }

    @Test
    void salesSeeOnlyTheirOwnOrdersAndOtherRolesAreRejected() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);
        Long responsibleSalesUserId = jdbcTemplate.queryForObject(
                "SELECT responsible_sales_user_id FROM sales_order WHERE id = ?",
                Long.class,
                order.salesOrderId()
        );

        //負責的業務看得到自己的 SO；沒帶負責業務時自動帶自己
        analysis(order.salesToken(), order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.newOrderAmount.current").value(2000));

        //其他業務：同一個客戶查不到這張 SO；指定別人回 403
        String otherSales = login(createUser("other-sales", "SALES"));
        analysis(otherSales, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.newOrderAmount.current").value(0));
        mockMvc.perform(get("/api/v1/analytics/sales")
                        .header("Authorization", bearer(otherSales))
                        .param("responsibleSalesUserId", responsibleSalesUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        //業務主管看全部，可以指定負責業務
        mockMvc.perform(get("/api/v1/analytics/sales")
                        .header("Authorization", bearer(salesManager))
                        .param("customerId", order.customer().getId().toString())
                        .param("responsibleSalesUserId", responsibleSalesUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.newOrderAmount.current").value(2000));

        //採購、財務沒有接單營收的權限
        analysis(login(createUser("purchasing", "PURCHASING")), order).andExpect(status().isForbidden());
        analysis(login(createUser("finance", "FINANCE")), order).andExpect(status().isForbidden());
    }

    @Test
    void defaultPeriodIsMonthToDateAndInvalidPeriodIsRejected() throws Exception {
        String manager = login(createUser("manager", "MANAGER"));

        mockMvc.perform(get("/api/v1/analytics/sales").header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value(today().withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.period.to").value(today().toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(today().withDayOfMonth(1).minusMonths(1).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(today().minusMonths(1).toString()));

        mockMvc.perform(get("/api/v1/analytics/sales")
                        .header("Authorization", bearer(manager))
                        .param("from", today().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ANALYTICS_PERIOD_INVALID"));
    }

    //本期是今天往前 9 天到今天，前期是再往前 10 天；用客戶篩選鎖定這張 SO
    private ResultActions analysis(String token, MainOrder order) throws Exception {
        return mockMvc.perform(get("/api/v1/analytics/sales")
                .header("Authorization", bearer(token))
                .param("from", today().minusDays(9).toString())
                .param("to", today().toString())
                .param("customerId", order.customer().getId().toString()));
    }

    private void setDeliveryDate(Long salesOrderLineId, LocalDate deliveryDate) {
        jdbcTemplate.update(
                "UPDATE sales_order_line SET delivery_date = ? WHERE id = ?",
                deliveryDate,
                salesOrderLineId
        );
    }

    private static BigDecimal sum(String body, String path) {
        List<Number> values = JsonPath.read(body, path);
        return values.stream()
                .map(value -> new BigDecimal(value.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
