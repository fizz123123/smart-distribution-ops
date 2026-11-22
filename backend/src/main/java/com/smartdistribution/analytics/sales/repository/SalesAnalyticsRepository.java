package com.smartdistribution.analytics.sales.repository;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.sales.repository.projection.AmountByKeyProjection;
import com.smartdistribution.analytics.sales.repository.projection.BacklogLineProjection;
import com.smartdistribution.analytics.sales.repository.projection.ContributionProjection;
import com.smartdistribution.analytics.sales.repository.projection.NewOrderTotalsProjection;
import com.smartdistribution.analytics.sales.repository.projection.OnTimeDeliveryProjection;
import com.smartdistribution.analytics.sales.repository.projection.RevenueTotalsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.smartdistribution.analytics.common.repository.SalesLineSql.BACKLOG_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.CANCELLATION_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.NEW_ORDER_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.REVENUE_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.SHIPPED_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.params;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.scope;

/**
 * 接單與營收分析的合計查詢：每種資料的明細 CTE 在 SalesLineSql，這裡只做期間篩選與加總
 * 金額都是未稅
 */
@Repository
@RequiredArgsConstructor
public class SalesAnalyticsRepository {

    //三個月份加總的查詢都回傳「年月 → 金額」
    private static final RowMapper<AmountByKeyProjection> MONTHLY_AMOUNT =
            (rs, rowNum) -> new AmountByKeyProjection(rs.getString("month_key"), rs.getBigDecimal("amount"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * @return 期間內的營收與有 DO 過帳的客戶數
     */
    public RevenueTotalsProjection findRevenueTotals(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = REVENUE_LINES + """
                SELECT COALESCE(SUM(amount), 0)    AS revenue,
                       COUNT(DISTINCT customer_id) AS active_customer_count
                FROM revenue_line
                WHERE business_date BETWEEN :from AND :to
                """;

        return jdbcTemplate.queryForObject(sql, params(filter, from, to), (rs, rowNum) -> new RevenueTotalsProjection(
                rs.getBigDecimal("revenue"),
                rs.getLong("active_customer_count")
        ));
    }

    /**
     * 有料號篩選時，接單額只算這個料號的明細，張數與客戶數只算含這個料號的 SO
     *
     * @return 期間內核准的接單額、SO 張數與下單客戶數
     */
    public NewOrderTotalsProjection findNewOrderTotals(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = NEW_ORDER_LINES + """
                SELECT COALESCE(SUM(amount), 0)       AS amount,
                       COUNT(DISTINCT sales_order_id) AS order_count,
                       COUNT(DISTINCT customer_id)    AS customer_count
                FROM new_order_line
                WHERE business_date BETWEEN :from AND :to
                """;

        return jdbcTemplate.queryForObject(sql, params(filter, from, to), (rs, rowNum) -> new NewOrderTotalsProjection(
                rs.getBigDecimal("amount"),
                rs.getLong("order_count"),
                rs.getLong("customer_count")
        ));
    }

    /**
     * @return 期間內核准的取消金額
     */
    public BigDecimal findCancellationAmount(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = CANCELLATION_LINES + """
                SELECT COALESCE(SUM(amount), 0) AS amount
                FROM cancellation_line
                WHERE business_date BETWEEN :from AND :to
                """;

        return jdbcTemplate.queryForObject(sql, params(filter, from, to), BigDecimal.class);
    }

    /**
     * @return 期間內出貨的明細筆數與其中準時的筆數；交期沒有值的舊資料算不準時
     */
    public OnTimeDeliveryProjection findOnTimeDelivery(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = SHIPPED_LINES + """
                SELECT COUNT(*)                            AS shipped_line_count,
                       COUNT(CASE WHEN on_time THEN 1 END) AS on_time_line_count
                FROM shipped_line
                WHERE business_date BETWEEN :from AND :to
                """;

        return jdbcTemplate.queryForObject(sql, params(filter, from, to), (rs, rowNum) -> new OnTimeDeliveryProjection(
                rs.getLong("shipped_line_count"),
                rs.getLong("on_time_line_count")
        ));
    }

    /**
     * @return 每個月的營收；鍵是 2026-11 這種年月，沒有營收的月份不會出現
     */
    public List<AmountByKeyProjection> findMonthlyRevenue(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        String sql = REVENUE_LINES + """
                SELECT DATE_FORMAT(business_date, '%Y-%m') AS month_key,
                       SUM(amount)                          AS amount
                FROM revenue_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY month_key
                """;

        return jdbcTemplate.query(sql, params(filter, from, to), MONTHLY_AMOUNT);
    }

    /**
     * @return 每個月的新增核准接單額
     */
    public List<AmountByKeyProjection> findMonthlyNewOrderAmounts(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to
    ) {
        String sql = NEW_ORDER_LINES + """
                SELECT DATE_FORMAT(business_date, '%Y-%m') AS month_key,
                       SUM(amount)                          AS amount
                FROM new_order_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY month_key
                """;

        return jdbcTemplate.query(sql, params(filter, from, to), MONTHLY_AMOUNT);
    }

    /**
     * @return 每個月的核准取消額
     */
    public List<AmountByKeyProjection> findMonthlyCancellationAmounts(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to
    ) {
        String sql = CANCELLATION_LINES + """
                SELECT DATE_FORMAT(business_date, '%Y-%m') AS month_key,
                       SUM(amount)                          AS amount
                FROM cancellation_line
                WHERE business_date BETWEEN :from AND :to
                GROUP BY month_key
                """;

        return jdbcTemplate.query(sql, params(filter, from, to), MONTHLY_AMOUNT);
    }

    /**
     * 本期與前期各自的營收，依客戶分組；兩期都沒有營收的客戶不會出現
     */
    public List<ContributionProjection> findRevenueByCustomer(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo
    ) {
        String sql = REVENUE_LINES + """
                SELECT customer.id,
                       customer.customer_code AS code,
                       customer.name,
                       COALESCE(SUM(CASE WHEN business_date BETWEEN :from AND :to
                                         THEN amount END), 0) AS current_amount,
                       COALESCE(SUM(CASE WHEN business_date BETWEEN :previousFrom AND :previousTo
                                         THEN amount END), 0) AS previous_amount
                FROM revenue_line
                JOIN customer ON customer.id = revenue_line.customer_id
                WHERE business_date BETWEEN :previousFrom AND :to
                GROUP BY customer.id, customer.customer_code, customer.name
                """;

        return queryContributions(sql, filter, from, to, previousFrom, previousTo);
    }

    /**
     * 本期與前期各自的營收，依公司料號分組；兩期都沒有營收的料號不會出現
     */
    public List<ContributionProjection> findRevenueByItem(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo
    ) {
        String sql = REVENUE_LINES + """
                SELECT company_item.id,
                       company_item.item_code AS code,
                       company_item.name,
                       COALESCE(SUM(CASE WHEN business_date BETWEEN :from AND :to
                                         THEN amount END), 0) AS current_amount,
                       COALESCE(SUM(CASE WHEN business_date BETWEEN :previousFrom AND :previousTo
                                         THEN amount END), 0) AS previous_amount
                FROM revenue_line
                JOIN company_item ON company_item.id = revenue_line.company_item_id
                WHERE business_date BETWEEN :previousFrom AND :to
                GROUP BY company_item.id, company_item.item_code, company_item.name
                """;

        return queryContributions(sql, filter, from, to, previousFrom, previousTo);
    }

    /**
     * 已接單未出貨的明細：狀態查詢，不受期間影響；合計和下鑽都用這份清單，供應狀態由 service 判斷
     *
     * @return 還有未出貨量的已核准 SO 明細
     */
    public List<BacklogLineProjection> findBacklogLines(AnalyticsFilter filter) {
        String sql = BACKLOG_LINES + """
                SELECT backlog_line.sales_order_line_id,
                       backlog_line.sales_order_id,
                       backlog_line.so_number,
                       backlog_line.line_no,
                       backlog_line.customer_id,
                       customer.customer_code,
                       customer.name AS customer_name,
                       backlog_line.company_item_id,
                       company_item.item_code,
                       company_item.name AS item_name,
                       backlog_line.delivery_date,
                       backlog_line.remaining_quantity,
                       backlog_line.unit_code,
                       ROUND(backlog_line.remaining_quantity * backlog_line.unit_price, 2) AS amount
                FROM backlog_line
                JOIN customer ON customer.id = backlog_line.customer_id
                JOIN company_item ON company_item.id = backlog_line.company_item_id
                WHERE backlog_line.remaining_quantity > 0
                ORDER BY backlog_line.delivery_date, backlog_line.sales_order_line_id
                """;

        return jdbcTemplate.query(sql, scope(filter), (rs, rowNum) -> new BacklogLineProjection(
                rs.getLong("sales_order_line_id"),
                rs.getLong("sales_order_id"),
                rs.getString("so_number"),
                rs.getInt("line_no"),
                rs.getLong("customer_id"),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getLong("company_item_id"),
                rs.getString("item_code"),
                rs.getString("item_name"),
                rs.getObject("delivery_date", LocalDate.class),
                rs.getBigDecimal("remaining_quantity"),
                rs.getString("unit_code"),
                rs.getBigDecimal("amount")
        ));
    }

    private List<ContributionProjection> queryContributions(
            String sql,
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo
    ) {
        MapSqlParameterSource params = params(filter, from, to)
                .addValue("previousFrom", previousFrom)
                .addValue("previousTo", previousTo);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ContributionProjection(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("current_amount"),
                rs.getBigDecimal("previous_amount")
        ));
    }
}
