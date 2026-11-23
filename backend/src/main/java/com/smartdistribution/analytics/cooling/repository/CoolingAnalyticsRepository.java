package com.smartdistribution.analytics.cooling.repository;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.cooling.repository.projection.CustomerOrdersProjection;
import com.smartdistribution.analytics.cooling.repository.projection.CustomerTradeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.smartdistribution.analytics.common.repository.SalesLineSql.BACKLOG_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.CANCELLATION_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.NEW_ORDER_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.REVENUE_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.SHIPPED_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.params;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.scope;

/**
 * 交易降溫的唯讀查詢：交易週期自己查；補充證據讀和接單營收分析同一段明細 CTE，
 * 所以某個客戶的營收、接單額，和接單營收分析篩這個客戶時的數字一樣
 * 結果都依客戶分組，回傳客戶 ID → 數值；沒有資料的客戶不會出現
 */
@Repository
@RequiredArgsConstructor
public class CoolingAnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 只算基準日以前已經核准、訂單日期也在基準日以前的 SO，看到的是那一天當時能知道的資料
     *
     * @param filter 客戶與負責業務；業務只看自己時，週期也只用自己負責的 SO 算
     * @return 有已核准 SO 的客戶
     */
    public List<CustomerTradeProjection> findCustomerTrades(AnalyticsFilter filter, LocalDate asOf) {
        String sql = """
                SELECT sales_order.customer_id,
                       customer.customer_code,
                       customer.name AS customer_name,
                       customer.status = 'ACTIVE' AS active,
                       COUNT(*) AS approved_order_count,
                       COUNT(DISTINCT sales_order.order_date) AS trade_day_count,
                       MIN(sales_order.order_date) AS first_trade_date,
                       MAX(sales_order.order_date) AS last_trade_date
                FROM sales_order
                JOIN customer ON customer.id = sales_order.customer_id
                WHERE sales_order.approved_at IS NOT NULL
                  AND DATE(sales_order.approved_at) <= :asOf
                  AND sales_order.order_date <= :asOf
                  AND (:customerId IS NULL OR sales_order.customer_id = :customerId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
                GROUP BY sales_order.customer_id, customer.customer_code, customer.name, customer.status
                """;

        return jdbcTemplate.query(sql, scope(filter).addValue("asOf", asOf), (rs, rowNum) ->
                new CustomerTradeProjection(
                        rs.getLong("customer_id"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getBoolean("active"),
                        rs.getInt("approved_order_count"),
                        rs.getInt("trade_day_count"),
                        rs.getObject("first_trade_date", LocalDate.class),
                        rs.getObject("last_trade_date", LocalDate.class)
                )
        );
    }

    /**
     * @return 客戶 ID → 實際出貨日在期間內的營收
     */
    public Map<Long, BigDecimal> findRevenueByCustomer(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = REVENUE_LINES + """
                SELECT customer_id,
                       SUM(amount) AS amount
                FROM revenue_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY customer_id
                """;

        Map<Long, BigDecimal> revenue = new HashMap<>();
        jdbcTemplate.query(sql, params(filter, from, to), (RowCallbackHandler) rs ->
                revenue.put(rs.getLong("customer_id"), rs.getBigDecimal("amount"))
        );
        return revenue;
    }

    /**
     * @return 客戶 ID → 核准日在期間內的 SO 張數與接單額
     */
    public Map<Long, CustomerOrdersProjection> findNewOrdersByCustomer(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to
    ) {
        String sql = NEW_ORDER_LINES + """
                SELECT customer_id,
                       COUNT(DISTINCT sales_order_id) AS order_count,
                       SUM(amount) AS amount
                FROM new_order_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY customer_id
                """;

        Map<Long, CustomerOrdersProjection> orders = new HashMap<>();
        jdbcTemplate.query(sql, params(filter, from, to), (RowCallbackHandler) rs -> orders.put(
                rs.getLong("customer_id"),
                new CustomerOrdersProjection(
                        rs.getLong("customer_id"),
                        rs.getInt("order_count"),
                        rs.getBigDecimal("amount")
                )
        ));
        return orders;
    }

    /**
     * @return 客戶 ID → 期間內核准的取消申請張數
     */
    public Map<Long, Integer> findCancellationCounts(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = CANCELLATION_LINES + """
                SELECT customer_id,
                       COUNT(DISTINCT cancellation_id) AS count
                FROM cancellation_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY customer_id
                """;

        return queryCounts(sql, params(filter, from, to));
    }

    /**
     * 延遲出貨：實際出貨日晚於 SO 明細交期的 DO 明細，和準時交貨率是同一個判斷
     *
     * @return 客戶 ID → 期間內延遲出貨的 DO 明細數
     */
    public Map<Long, Integer> findLateShipmentCounts(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = SHIPPED_LINES + """
                SELECT customer_id,
                       COUNT(CASE WHEN NOT on_time THEN 1 END) AS count
                FROM shipped_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY customer_id
                """;

        return queryCounts(sql, params(filter, from, to));
    }

    /**
     * 缺料的明細一定還沒出貨，所以只要算這幾筆的供應狀態；和已接單未出貨讀同一段 CTE
     *
     * @return 還有未出貨量的已核准 SO 明細 ID
     */
    public List<Long> findBacklogLineIds(AnalyticsFilter filter) {
        String sql = BACKLOG_LINES + """
                SELECT sales_order_line_id
                FROM backlog_line
                WHERE remaining_quantity > 0
                """;

        return jdbcTemplate.queryForList(sql, scope(filter), Long.class);
    }

    private Map<Long, Integer> queryCounts(String sql, MapSqlParameterSource params) {
        Map<Long, Integer> counts = new HashMap<>();
        jdbcTemplate.query(sql, params, (RowCallbackHandler) rs ->
                counts.put(rs.getLong("customer_id"), rs.getInt("count"))
        );
        return counts;
    }
}
