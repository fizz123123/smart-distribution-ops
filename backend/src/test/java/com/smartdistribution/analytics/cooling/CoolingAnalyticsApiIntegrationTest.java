package com.smartdistribution.analytics.cooling;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.masterdata.customeritemmapping.entity.CustomerItemMapping;
import com.smartdistribution.receivable.ReceivableTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客戶交易降溫：比值與等級、同一天多張只算一次、依平均月營收排序、asOf、停用客戶與業務範圍
 * 業務只看自己負責的 SO，所以用新建的業務查詢，清單只會有這個測試建立的客戶
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoolingAnalyticsApiIntegrationTest extends ReceivableTestSupport {

    @Test
    void levelsFollowCycleRatioAndSameLevelIsSortedByMonthlyRevenue() throws Exception {
        String sales = login(createUser("cooling-sales", "SALES"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        String warehouseManager = login(createUser("wh-manager", "WAREHOUSE_MANAGER"));
        String warehouse = login(createUser("warehouse", "WAREHOUSE"));

        //週期都是 10 天：距上次 40 天是 4 倍、16 天是 1.6 倍、5 天是 0.5 倍
        CustomerOrders high = createOrders(sales, salesManager, null, daysAgo(60), daysAgo(50), daysAgo(40));
        CustomerOrders medium = createOrders(sales, salesManager, null, daysAgo(36), daysAgo(26), daysAgo(16));
        CustomerOrders calm = createOrders(sales, salesManager, null, daysAgo(25), daysAgo(15), daysAgo(5));
        //同一天兩張只算一個交易日：交易日只有 2 個，資料不足
        CustomerOrders sparse = createOrders(sales, salesManager, null, daysAgo(30), daysAgo(30), daysAgo(10));
        //和 high 一樣是 HIGH，但 60 天前出貨 100 元：平均月營收比較高，排在前面；交期是 62 天前，所以算延遲出貨
        CustomerOrders highWithRevenue = createOrders(
                sales, salesManager, warehouseManager, daysAgo(61), daysAgo(51), daysAgo(41)
        );
        Long shippedOrderId = highWithRevenue.salesOrderIds().getFirst();
        Long shippedLineId = findLineIds(shippedOrderId).getFirst();
        jdbcTemplate.update(
                "UPDATE sales_order_line SET delivery_date = ? WHERE id = ?",
                daysAgo(62),
                shippedLineId
        );
        Long deliveryOrderId = createDeliveryOrder(sales, shippedOrderId, doLine(shippedLineId, "1"));
        ready(warehouse, deliveryOrderId, actualLine(findDeliveryOrderLineIds(deliveryOrderId).getFirst(), "1"))
                .andExpect(status().isOk());
        postShipment(warehouse, deliveryOrderId, daysAgo(60)).andExpect(status().isOk());

        String body = cooling(sales)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(today().toString()))
                .andExpect(jsonPath("$.customers.length()").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Integer> order = JsonPath.read(body, "$.customers[*].customerId");
        assertThat(order).containsExactly(
                id(highWithRevenue), id(high), id(medium), id(calm), id(sparse)
        );

        cooling(sales)
                //HIGH：週期 10 天，距上次 41 天
                .andExpect(jsonPath("$.customers[0].level").value("HIGH"))
                .andExpect(jsonPath("$.customers[0].daysSinceLastTrade").value(41))
                .andExpect(jsonPath("$.customers[0].averageCycleDays").value(10))
                .andExpect(jsonPath("$.customers[0].cycleRatio").value(4.1))
                //100 ÷ 6
                .andExpect(jsonPath("$.customers[0].averageMonthlyRevenue").value(16.67))
                //補充證據：本期 70 天內出貨 100 元而且晚交，三張 SO 今天核准；還沒出貨的兩張缺料
                .andExpect(jsonPath("$.customers[0].evidence.revenue.current").value(100))
                .andExpect(jsonPath("$.customers[0].evidence.revenue.note").value("NO_PRIOR_BASELINE"))
                .andExpect(jsonPath("$.customers[0].evidence.orderCount.current").value(3))
                .andExpect(jsonPath("$.customers[0].evidence.orderAmount.current").value(300))
                .andExpect(jsonPath("$.customers[0].evidence.shortageLineCount").value(2))
                .andExpect(jsonPath("$.customers[0].evidence.lateShipmentLineCount").value(1))
                .andExpect(jsonPath("$.customers[1].level").value("HIGH"))
                .andExpect(jsonPath("$.customers[1].averageMonthlyRevenue").value(0))
                .andExpect(jsonPath("$.customers[1].evidence.shortageLineCount").value(3))
                .andExpect(jsonPath("$.customers[2].level").value("MEDIUM"))
                .andExpect(jsonPath("$.customers[2].cycleRatio").value(1.6))
                //未達警示：沒有等級，也沒有附註
                .andExpect(jsonPath("$.customers[3].level").doesNotExist())
                .andExpect(jsonPath("$.customers[3].note").doesNotExist())
                .andExpect(jsonPath("$.customers[3].cycleRatio").value(0.5))
                .andExpect(jsonPath("$.customers[4].note").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.customers[4].approvedOrderCount").value(3))
                .andExpect(jsonPath("$.customers[4].tradeDayCount").value(2))
                .andExpect(jsonPath("$.customers[4].averageCycleDays").doesNotExist());
    }

    @Test
    void asOfOnlyCountsOrdersApprovedByThatDay() throws Exception {
        String sales = login(createUser("cooling-sales", "SALES"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        CustomerOrders orders = createOrders(sales, salesManager, null, daysAgo(30), daysAgo(20), daysAgo(10));
        //實務上 SO 在訂單日期當天核准；第二張晚了 8 天才核准
        setApprovedDate(orders.salesOrderIds().get(0), daysAgo(30));
        setApprovedDate(orders.salesOrderIds().get(1), daysAgo(12));
        setApprovedDate(orders.salesOrderIds().get(2), daysAgo(10));

        //15 天前：第二張還沒核准、第三張還沒下單，只看得到第一張
        mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                        .header("Authorization", bearer(sales))
                        .param("asOf", daysAgo(15).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(daysAgo(15).toString()))
                .andExpect(jsonPath("$.customers.length()").value(1))
                .andExpect(jsonPath("$.customers[0].approvedOrderCount").value(1))
                .andExpect(jsonPath("$.customers[0].lastTradeDate").value(daysAgo(30).toString()))
                .andExpect(jsonPath("$.customers[0].daysSinceLastTrade").value(15))
                .andExpect(jsonPath("$.customers[0].note").value("INSUFFICIENT_DATA"));

        //基準日不能是未來
        mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                        .header("Authorization", bearer(sales))
                        .param("asOf", today().plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ANALYTICS_PERIOD_INVALID"));
    }

    @Test
    void inactiveCustomerIsNotRatedAndSalesSeeOnlyTheirOwnOrders() throws Exception {
        String sales = login(createUser("cooling-sales", "SALES"));
        String salesManager = login(createUser("sales-manager", "SALES_MANAGER"));
        CustomerOrders orders = createOrders(sales, salesManager, null, daysAgo(60), daysAgo(50), daysAgo(40));
        jdbcTemplate.update("UPDATE customer SET status = 'INACTIVE' WHERE id = ?", orders.customer().getId());

        //比值照算，但停用的客戶不評等級
        cooling(sales)
                .andExpect(jsonPath("$.customers[0].active").value(false))
                .andExpect(jsonPath("$.customers[0].cycleRatio").value(4))
                .andExpect(jsonPath("$.customers[0].level").doesNotExist());

        //其他業務看不到這個客戶；指定別人回 403
        String otherSales = login(createUser("other-sales", "SALES"));
        mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                        .header("Authorization", bearer(otherSales))
                        .param("customerId", orders.customer().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.length()").value(0));
        Long salesUserId = jdbcTemplate.queryForObject(
                "SELECT responsible_sales_user_id FROM sales_order WHERE id = ?",
                Long.class,
                orders.salesOrderIds().getFirst()
        );
        mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                        .header("Authorization", bearer(otherSales))
                        .param("responsibleSalesUserId", salesUserId.toString()))
                .andExpect(status().isForbidden());

        //業務主管看全部；財務沒有權限
        mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                        .header("Authorization", bearer(salesManager))
                        .param("customerId", orders.customer().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.length()").value(1));
        mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                        .header("Authorization", bearer(login(createUser("finance", "FINANCE")))))
                .andExpect(status().isForbidden());
    }

    //補充證據的本期：今天往前 69 天到今天，涵蓋測試裡的出貨與核准
    private ResultActions cooling(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/analytics/customer-cooling")
                .header("Authorization", bearer(token))
                .param("from", daysAgo(69).toString())
                .param("to", today().toString()));
    }

    /**
     * 一個客戶、一個料號，依序在每個訂單日期下一張 1 個 @ 100 的 SO 並核准
     *
     * @param warehouseManager 不是 null 時先建 1 個期初庫存，第一張 SO 核准後會預留到，可以出貨
     */
    private CustomerOrders createOrders(
            String sales,
            String salesManager,
            String warehouseManager,
            LocalDate... orderDates
    ) throws Exception {
        Customer customer = createActiveCustomer(PaymentTerm.NET_30);
        CompanyItem item = createActiveItem(UnitOfMeasure.PCS);
        CustomerItemMapping mapping = createActiveMapping(customer, item);
        createActivePrice(mapping, UnitOfMeasure.PCS, "100");
        if (warehouseManager != null) {
            postOpeningBalance(warehouseManager, item, "1");
        }

        List<Long> salesOrderIds = new ArrayList<>();
        for (LocalDate orderDate : orderDates) {
            Long salesOrderId = createSubmittedOrder(
                    sales, draftBody(customer, orderDate, line(mapping, "1", UnitOfMeasure.PCS))
            );
            approve(salesManager, salesOrderId).andExpect(status().isOk());
            salesOrderIds.add(salesOrderId);
        }

        return new CustomerOrders(customer, salesOrderIds);
    }

    //核准時間 API 設定不到，直接改
    private void setApprovedDate(Long salesOrderId, LocalDate approvedDate) {
        jdbcTemplate.update(
                "UPDATE sales_order SET approved_at = ? WHERE id = ?",
                approvedDate.atTime(12, 0),
                salesOrderId
        );
    }

    private static LocalDate daysAgo(int days) {
        return today().minusDays(days);
    }

    private static int id(CustomerOrders orders) {
        return orders.customer().getId().intValue();
    }

    private record CustomerOrders(Customer customer, List<Long> salesOrderIds) {
    }
}
