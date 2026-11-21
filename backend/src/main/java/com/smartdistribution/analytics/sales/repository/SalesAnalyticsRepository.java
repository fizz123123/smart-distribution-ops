package com.smartdistribution.analytics.sales.repository;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 接單與營收分析的唯讀查詢
 */
@Repository
@RequiredArgsConstructor
public class SalesAnalyticsRepository {

    //營收：每一筆應收明細，日期是 DO 實際出貨日；金額在 DO 過帳時已經算好
    private static final String REVENUE_LINES = """
            WITH revenue_line AS (
                SELECT receivable.actual_shipment_date AS business_date,
                       receivable.customer_id,
                       receivable_line.company_item_id,
                       receivable_line.line_amount_excluding_tax AS amount
                FROM receivable_line
                JOIN receivable ON receivable.id = receivable_line.receivable_id
                JOIN sales_order ON sales_order.id = receivable.sales_order_id
                WHERE (:customerId IS NULL OR receivable.customer_id = :customerId)
                  AND (:companyItemId IS NULL OR receivable_line.company_item_id = :companyItemId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
            )
            """;

    //新增接單：已核准 SO 的每一筆明細，日期是核准日；SO 核准後不會再核准，所以就是首次核准
    private static final String NEW_ORDER_LINES = """
            WITH new_order_line AS (
                SELECT DATE(sales_order.approved_at) AS business_date,
                       sales_order.id AS sales_order_id,
                       sales_order.customer_id,
                       sales_order_line.line_amount_excluding_tax AS amount
                FROM sales_order_line
                JOIN sales_order ON sales_order.id = sales_order_line.sales_order_id
                WHERE sales_order.approved_at IS NOT NULL
                  AND (:customerId IS NULL OR sales_order.customer_id = :customerId)
                  AND (:companyItemId IS NULL OR sales_order_line.company_item_id = :companyItemId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
            )
            """;

    //核准取消：已核准取消申請的每一行，金額 ＝ 取消數量 × SO 明細單價，和 SO 的有效金額同一個算法
    private static final String CANCELLATION_LINES = """
            WITH cancellation_line AS (
                SELECT DATE(cancellation.approved_at) AS business_date,
                       ROUND(cancel_line.quantity * sales_order_line.unit_price_excluding_tax, 2) AS amount
                FROM sales_order_cancellation_line cancel_line
                JOIN sales_order_cancellation cancellation ON cancellation.id = cancel_line.cancellation_id
                JOIN sales_order_line ON sales_order_line.id = cancel_line.sales_order_line_id
                JOIN sales_order ON sales_order.id = sales_order_line.sales_order_id
                WHERE cancellation.status = 'APPROVED'
                  AND (:customerId IS NULL OR sales_order.customer_id = :customerId)
                  AND (:companyItemId IS NULL OR sales_order_line.company_item_id = :companyItemId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
            )
            """;

    //準時交貨：已過帳 DO 的每一筆出貨明細，實際出貨日不晚於 SO 明細交期就算準時
    private static final String SHIPPED_LINES = """
            WITH shipped_line AS (
                SELECT delivery_order.actual_shipment_date AS business_date,
                       delivery_order.actual_shipment_date <= sales_order_line.delivery_date AS on_time
                FROM delivery_order_line
                JOIN delivery_order ON delivery_order.id = delivery_order_line.delivery_order_id
                JOIN sales_order_line ON sales_order_line.id = delivery_order_line.sales_order_line_id
                JOIN sales_order ON sales_order.id = sales_order_line.sales_order_id
                WHERE delivery_order.status = 'POSTED'
                  AND delivery_order_line.actual_quantity > 0
                  AND (:customerId IS NULL OR sales_order.customer_id = :customerId)
                  AND (:companyItemId IS NULL OR delivery_order_line.company_item_id = :companyItemId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
            )
            """;

    //已接單未出貨：已核准 SO 的每一筆明細，未出貨 ＝ 訂購 － 已核准取消 － 已過帳出貨（都是訂購單位）
    private static final String BACKLOG_LINES = """
            WITH cancelled AS (
                SELECT cancel_line.sales_order_line_id,
                       SUM(cancel_line.quantity) AS quantity
                FROM sales_order_cancellation_line cancel_line
                JOIN sales_order_cancellation cancellation ON cancellation.id = cancel_line.cancellation_id
                WHERE cancellation.status = 'APPROVED'
                GROUP BY cancel_line.sales_order_line_id
            ),
            shipped AS (
                SELECT delivery_order_line.sales_order_line_id,
                       SUM(delivery_order_line.actual_quantity) AS quantity
                FROM delivery_order_line
                JOIN delivery_order ON delivery_order.id = delivery_order_line.delivery_order_id
                WHERE delivery_order.status = 'POSTED'
                GROUP BY delivery_order_line.sales_order_line_id
            ),
            backlog_line AS (
                SELECT sales_order_line.id AS sales_order_line_id,
                       sales_order.id AS sales_order_id,
                       sales_order.so_number,
                       sales_order_line.line_no,
                       sales_order.customer_id,
                       sales_order_line.company_item_id,
                       sales_order_line.delivery_date,
                       sales_order_line.quantity
                           - COALESCE(cancelled.quantity, 0)
                           - COALESCE(shipped.quantity, 0) AS remaining_quantity,
                       sales_order_line.unit_price_excluding_tax AS unit_price
                FROM sales_order_line
                JOIN sales_order ON sales_order.id = sales_order_line.sales_order_id
                LEFT JOIN cancelled ON cancelled.sales_order_line_id = sales_order_line.id
                LEFT JOIN shipped ON shipped.sales_order_line_id = sales_order_line.id
                WHERE sales_order.status = 'APPROVED'
                  AND (:customerId IS NULL OR sales_order.customer_id = :customerId)
                  AND (:companyItemId IS NULL OR sales_order_line.company_item_id = :companyItemId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
            )
            """;

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
     * 狀態查詢，不受期間影響
     *
     * @return 已核准 SO 中還有未出貨量的明細，依交期排序
     */
    public List<BacklogLineProjection> findBacklogLines(AnalyticsFilter filter) {
        String sql = BACKLOG_LINES + """
                SELECT sales_order_line_id,
                       sales_order_id,
                       so_number,
                       line_no,
                       customer_id,
                       company_item_id,
                       delivery_date,
                       remaining_quantity,
                       ROUND(remaining_quantity * unit_price, 2) AS amount
                FROM backlog_line
                WHERE remaining_quantity > 0
                ORDER BY delivery_date, sales_order_line_id
                """;

        return jdbcTemplate.query(sql, scope(filter), (rs, rowNum) -> new BacklogLineProjection(
                rs.getLong("sales_order_line_id"),
                rs.getLong("sales_order_id"),
                rs.getString("so_number"),
                rs.getInt("line_no"),
                rs.getLong("customer_id"),
                rs.getLong("company_item_id"),
                rs.getObject("delivery_date", LocalDate.class),
                rs.getBigDecimal("remaining_quantity"),
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

    private static MapSqlParameterSource params(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        return scope(filter)
                .addValue("from", from)
                .addValue("to", to);
    }

    private static MapSqlParameterSource scope(AnalyticsFilter filter) {
        return new MapSqlParameterSource()
                .addValue("customerId", filter.customerId())
                .addValue("companyItemId", filter.companyItemId())
                .addValue("responsibleSalesUserId", filter.responsibleSalesUserId());
    }
}
