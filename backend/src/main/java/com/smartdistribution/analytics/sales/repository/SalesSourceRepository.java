package com.smartdistribution.analytics.sales.repository;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import com.smartdistribution.analytics.sales.repository.projection.CancellationSourceProjection;
import com.smartdistribution.analytics.sales.repository.projection.NewOrderSourceProjection;
import com.smartdistribution.analytics.sales.repository.projection.RevenueSourceProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.smartdistribution.analytics.common.repository.SalesLineSql.CANCELLATION_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.NEW_ORDER_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.REVENUE_LINES;
import static com.smartdistribution.analytics.common.repository.SalesLineSql.params;

/**
 * 營收、新增接單、核准取消的來源明細，給儀表板下鑽用
 * 和 SalesAnalyticsRepository 讀同一段明細 CTE，所以同樣的期間與篩選下，明細加起來等於合計
 * 排序固定：日期由新到舊，同一天再依 ID 由新到舊
 */
@Repository
@RequiredArgsConstructor
public class SalesSourceRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * @return 期間內出貨的應收明細，一頁
     */
    public Page<RevenueSourceProjection> findRevenueSources(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        String sql = REVENUE_LINES + """
                SELECT revenue_line.*,
                       customer.customer_code,
                       customer.name AS customer_name,
                       company_item.item_code,
                       company_item.name AS item_name
                FROM revenue_line
                JOIN customer ON customer.id = revenue_line.customer_id
                JOIN company_item ON company_item.id = revenue_line.company_item_id
                WHERE business_date BETWEEN :from AND :to
                ORDER BY business_date DESC, line_id DESC
                LIMIT :limit OFFSET :offset
                """;
        String countSql = REVENUE_LINES + """
                SELECT COUNT(*)
                FROM revenue_line
                WHERE business_date BETWEEN :from AND :to
                """;

        MapSqlParameterSource params = pageParams(filter, from, to, pageable);
        List<RevenueSourceProjection> content = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new RevenueSourceProjection(
                        rs.getObject("business_date", LocalDate.class),
                        rs.getLong("receivable_id"),
                        rs.getString("receivable_number"),
                        rs.getLong("delivery_order_id"),
                        rs.getString("do_number"),
                        rs.getLong("sales_order_id"),
                        rs.getString("so_number"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getLong("company_item_id"),
                        rs.getString("item_code"),
                        rs.getString("item_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit_code"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("amount")
                )
        );

        return new PageImpl<>(content, pageable, count(countSql, params));
    }

    /**
     * 一張 SO 一筆；有料號篩選時，只列含這個料號的 SO，金額也只算這個料號的明細
     *
     * @return 期間內核准的 SO，一頁
     */
    public Page<NewOrderSourceProjection> findNewOrderSources(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        String sql = NEW_ORDER_LINES + """
                SELECT new_order_line.business_date,
                       new_order_line.sales_order_id,
                       new_order_line.so_number,
                       new_order_line.customer_id,
                       customer.customer_code,
                       customer.name AS customer_name,
                       new_order_line.responsible_sales_user_id,
                       app_user.name AS responsible_sales_name,
                       SUM(new_order_line.amount) AS amount
                FROM new_order_line
                JOIN customer ON customer.id = new_order_line.customer_id
                JOIN app_user ON app_user.id = new_order_line.responsible_sales_user_id
                WHERE business_date BETWEEN :from AND :to
                GROUP BY new_order_line.business_date,
                         new_order_line.sales_order_id,
                         new_order_line.so_number,
                         new_order_line.customer_id,
                         customer.customer_code,
                         customer.name,
                         new_order_line.responsible_sales_user_id,
                         app_user.name
                ORDER BY business_date DESC, sales_order_id DESC
                LIMIT :limit OFFSET :offset
                """;
        String countSql = NEW_ORDER_LINES + """
                SELECT COUNT(DISTINCT sales_order_id)
                FROM new_order_line
                WHERE business_date BETWEEN :from AND :to
                """;

        MapSqlParameterSource params = pageParams(filter, from, to, pageable);
        List<NewOrderSourceProjection> content = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new NewOrderSourceProjection(
                        rs.getObject("business_date", LocalDate.class),
                        rs.getLong("sales_order_id"),
                        rs.getString("so_number"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getLong("responsible_sales_user_id"),
                        rs.getString("responsible_sales_name"),
                        rs.getBigDecimal("amount")
                )
        );

        return new PageImpl<>(content, pageable, count(countSql, params));
    }

    /**
     * @return 期間內核准的取消申請明細，一頁
     */
    public Page<CancellationSourceProjection> findCancellationSources(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        String sql = CANCELLATION_LINES + """
                SELECT cancellation_line.*,
                       customer.customer_code,
                       customer.name AS customer_name,
                       company_item.item_code,
                       company_item.name AS item_name
                FROM cancellation_line
                JOIN customer ON customer.id = cancellation_line.customer_id
                JOIN company_item ON company_item.id = cancellation_line.company_item_id
                WHERE business_date BETWEEN :from AND :to
                ORDER BY business_date DESC, line_id DESC
                LIMIT :limit OFFSET :offset
                """;
        String countSql = CANCELLATION_LINES + """
                SELECT COUNT(*)
                FROM cancellation_line
                WHERE business_date BETWEEN :from AND :to
                """;

        MapSqlParameterSource params = pageParams(filter, from, to, pageable);
        List<CancellationSourceProjection> content = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new CancellationSourceProjection(
                        rs.getObject("business_date", LocalDate.class),
                        rs.getLong("cancellation_id"),
                        rs.getString("cancellation_number"),
                        rs.getLong("sales_order_id"),
                        rs.getString("so_number"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getLong("company_item_id"),
                        rs.getString("item_code"),
                        rs.getString("item_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit_code"),
                        rs.getBigDecimal("amount")
                )
        );

        return new PageImpl<>(content, pageable, count(countSql, params));
    }

    private long count(String countSql, MapSqlParameterSource params) {
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);
        return total == null ? 0 : total;
    }

    //分頁：limit 是每頁筆數，offset 是要跳過的筆數
    private static MapSqlParameterSource pageParams(
            AnalyticsFilter filter,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        return params(filter, from, to)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
    }
}
