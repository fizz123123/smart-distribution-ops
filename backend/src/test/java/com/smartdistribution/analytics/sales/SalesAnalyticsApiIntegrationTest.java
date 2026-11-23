package com.smartdistribution.analytics.sales;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
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
 * 來源明細和指標讀同一組明細 CTE，所以來源加總要等於指標
 * 期間型指標自己帶 from、to（今天往前 9 天到今天），避免月初跑測試時「昨天」落在上個月
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesAnalyticsApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void periodComparisonTrendContributionsAndBacklog() throws Exception {
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        MainOrder order = createScenario(salesManager);

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
                //PU 剩下的 1 個已經預留，供應狀態是已備齊，沒有缺料影響
                .andExpect(jsonPath("$.backlog.shortageImpactAmount").value(0))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[5].status").value("READY"))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[5].amount").value(200))
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
    void sourcesAddUpToTheMetricsAndArePaged() throws Exception {
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        MainOrder order = createScenario(salesManager);

        //營收來源：本期兩次出貨各 2 筆明細，合計等於營收 1,000；最新的出貨排在前面
        MvcResult revenue = sources(salesManager, order, "revenue-sources", today().minusDays(9), today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].shipmentDate").value(today().toString()))
                .andExpect(jsonPath("$.content[0].soNumber").isNotEmpty())
                .andExpect(jsonPath("$.content[0].doNumber").isNotEmpty())
                .andReturn();
        assertThat(sum(revenue, "$.content[*].amount")).isEqualByComparingTo("1000");

        //前端用回應裡的 previousFrom、previousTo 查前期來源：合計等於前期營收 600
        MvcResult previousRevenue = sources(
                salesManager, order, "revenue-sources", today().minusDays(19), today().minusDays(10)
        ).andExpect(status().isOk()).andReturn();
        assertThat(sum(previousRevenue, "$.content[*].amount")).isEqualByComparingTo("600");

        //新增接單來源：一張 SO 一列，合計 2,000
        sources(salesManager, order, "new-order-sources", today().minusDays(9), today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].salesOrderId").value(order.salesOrderId()))
                .andExpect(jsonPath("$.content[0].responsibleSalesName").isNotEmpty())
                .andExpect(jsonPath("$.content[0].amount").value(2000));

        //核准取消來源：PU 取消 1 個，合計 200
        sources(salesManager, order, "cancellation-sources", today().minusDays(9), today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyItemId").value(order.pu().getId()))
                .andExpect(jsonPath("$.content[0].quantity").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(200));

        //分頁：一頁 3 筆，第二頁剩 1 筆
        mockMvc.perform(get("/api/v1/analytics/sales/revenue-sources")
                        .header("Authorization", bearer(salesManager))
                        .param("from", today().minusDays(9).toString())
                        .param("to", today().toString())
                        .param("customerId", order.customer().getId().toString())
                        .param("page", "1")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));

        //排序固定：帶 sort 和其他清單 API 遇到不支援的排序一樣回 400
        mockMvc.perform(get("/api/v1/analytics/sales/revenue-sources")
                        .header("Authorization", bearer(salesManager))
                        .param("customerId", order.customer().getId().toString())
                        .param("sort", "amount,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void backlogIsBrokenDownBySupplyStatus() throws Exception {
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        MainOrder order = createScenario(salesManager);

        //同一個客戶再下一張沒有庫存的 SO：3 個 @ 50，核准後預留不到，整行缺料待處理
        CompanyItem fitting = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping fittingMapping = createActiveMapping(order.customer(), fitting);
        createActivePrice(fittingMapping, UnitOfMeasure.PCS, "50");
        Long shortOrderId = createSubmittedOrder(
                order.salesToken(),
                draftBody(order.customer(), line(fittingMapping, "3", UnitOfMeasure.PCS))
        );
        approve(salesManager, shortOrderId).andExpect(status().isOk());
        Long fittingLineId = findLineIds(shortOrderId).getFirst();
        setDeliveryDate(fittingLineId, today().plusDays(30));

        //未出貨 350：PU 1 個已預留但逾期（已備齊 200）＋ 新料號缺料（缺料待處理 150）
        MvcResult result = analysis(salesManager, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backlog.amount").value(350))
                .andExpect(jsonPath("$.backlog.lineCount").value(2))
                .andExpect(jsonPath("$.backlog.overdueAmount").value(200))
                .andExpect(jsonPath("$.backlog.shortageImpactAmount").value(150))
                .andExpect(jsonPath("$.backlog.shortageImpactLineCount").value(1))
                .andExpect(jsonPath("$.backlog.bySupplyStatus.length()").value(6))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[0].status").value("UNPLANNED"))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[0].amount").value(150))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[0].lineCount").value(1))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[5].status").value("READY"))
                .andExpect(jsonPath("$.backlog.bySupplyStatus[5].amount").value(200))
                .andReturn();
        //各狀態加起來等於未出貨總額
        assertThat(sum(result, "$.backlog.bySupplyStatus[*].amount")).isEqualByComparingTo("350");

        //下鑽：依供應狀態
        backlogSources(salesManager, order, "supplyStatus", "UNPLANNED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].salesOrderLineId").value(fittingLineId))
                .andExpect(jsonPath("$.content[0].itemCode").value(fitting.getItemCode()))
                .andExpect(jsonPath("$.content[0].remainingQuantity").value(3))
                .andExpect(jsonPath("$.content[0].amount").value(150))
                .andExpect(jsonPath("$.content[0].overdue").value(false));

        //下鑽：只看逾期
        backlogSources(salesManager, order, "overdueOnly", "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].salesOrderLineId").value(order.puLineId()))
                .andExpect(jsonPath("$.content[0].supplyStatus").value("READY"))
                .andExpect(jsonPath("$.content[0].overdue").value(true));

        //不篩選時加起來等於未出貨金額，交期早的在前
        MvcResult all = backlogSources(salesManager, order, "page", "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].salesOrderLineId").value(order.puLineId()))
                .andReturn();
        assertThat(sum(all, "$.content[*].amount")).isEqualByComparingTo("350");

        //分頁在記憶體切：一頁 1 筆，第二頁是缺料的那一行
        mockMvc.perform(get("/api/v1/analytics/sales/backlog-sources")
                        .header("Authorization", bearer(salesManager))
                        .param("customerId", order.customer().getId().toString())
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].salesOrderLineId").value(fittingLineId))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void sourcesFollowTheSameScopeAsTheAnalysis() throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        MainOrder order = createMainOrder(warehouseManager, salesManager, PaymentTerm.NET_30);
        Long responsibleSalesUserId = jdbcTemplate.queryForObject(
                "SELECT responsible_sales_user_id FROM sales_order WHERE id = ?",
                Long.class,
                order.salesOrderId()
        );

        //負責的業務看得到；其他業務查不到，指定別人回 403
        sources(order.salesToken(), order, "new-order-sources", today().minusDays(9), today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        String otherSales = login(createUser("other-sales", "SALES"));
        sources(otherSales, order, "new-order-sources", today().minusDays(9), today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/analytics/sales/new-order-sources")
                        .header("Authorization", bearer(otherSales))
                        .param("responsibleSalesUserId", responsibleSalesUserId.toString()))
                .andExpect(status().isForbidden());

        //沒有接單營收權限的角色也看不到來源
        sources(login(createUser("finance", "FINANCE")), order, "revenue-sources", today().minusDays(9), today())
                .andExpect(status().isForbidden());
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

    /**
     * 共用情境：SO_MAIN 今天核准（PE 10 × 100 ＋ PU 5 × 200 ＝ 2,000），
     * 前期出貨營收 600、本期兩次出貨營收 1,000，核准取消 PU 1 個（200），PU 剩 1 個逾期未出貨
     */
    private MainOrder createScenario(String salesManager) throws Exception {
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));
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
        return order;
    }

    //來源明細：路徑是 /api/v1/analytics/sales/{type}，用客戶篩選鎖定這張 SO
    private ResultActions sources(String token, MainOrder order, String type, LocalDate from, LocalDate to)
            throws Exception {
        return mockMvc.perform(get("/api/v1/analytics/sales/" + type)
                .header("Authorization", bearer(token))
                .param("from", from.toString())
                .param("to", to.toString())
                .param("customerId", order.customer().getId().toString()));
    }

    //已接單未出貨的下鑽：不用期間，帶一個篩選參數；用客戶篩選鎖定這個情境的 SO
    private ResultActions backlogSources(String token, MainOrder order, String name, String value)
            throws Exception {
        return mockMvc.perform(get("/api/v1/analytics/sales/backlog-sources")
                .header("Authorization", bearer(token))
                .param("customerId", order.customer().getId().toString())
                .param(name, value));
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

    private static BigDecimal sum(MvcResult result, String path) throws Exception {
        return sum(result.getResponse().getContentAsString(), path);
    }

    private static BigDecimal sum(String body, String path) {
        List<Number> values = JsonPath.read(body, path);
        return values.stream()
                .map(value -> new BigDecimal(value.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
