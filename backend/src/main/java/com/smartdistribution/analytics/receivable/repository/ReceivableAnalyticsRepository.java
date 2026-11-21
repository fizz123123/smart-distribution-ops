package com.smartdistribution.analytics.receivable.repository;

import com.smartdistribution.analytics.receivable.repository.projection.OpenReceivableProjection;
import com.smartdistribution.analytics.receivable.repository.projection.ReceivableTotalsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 應收分析的唯讀查詢：直接用 SQL 讀應收、收款與 SO 的表，只查不改
 * analytics 沒有自己的 entity，做的是跨表加總，所以不用 Spring Data JPA，改用 JdbcTemplate
 * 未收餘額 ＝ 含稅總額 － 已確認收款的核銷加總，和應收模組的 ReceivableBalances 同一個算法
 */
@Repository
@RequiredArgsConstructor
public class ReceivableAnalyticsRepository {

    //每筆應收的未收餘額，兩個查詢都從這裡開始讀
    //paid：已確認收款的核銷加總，作廢的收款不算
    //receivable_balance：加上客戶、是否已確認、未收餘額，並套用客戶與負責業務的篩選
    private static final String RECEIVABLE_BALANCES = """
            WITH paid AS (
                SELECT allocation.receivable_id,
                       SUM(allocation.allocated_amount) AS paid_amount
                FROM payment_allocation allocation
                JOIN payment ON payment.id = allocation.payment_id
                WHERE payment.status = 'CONFIRMED'
                GROUP BY allocation.receivable_id
            ),
            receivable_balance AS (
                SELECT receivable.id,
                       receivable.receivable_number,
                       receivable.status,
                       receivable.status <> 'PENDING_CONFIRMATION' AS confirmed,
                       receivable.customer_id,
                       customer.customer_code,
                       customer.name AS customer_name,
                       receivable.sales_order_id,
                       receivable.so_number,
                       receivable.delivery_order_id,
                       receivable.do_number,
                       receivable.due_date,
                       receivable.total_amount,
                       receivable.total_amount - COALESCE(paid.paid_amount, 0) AS outstanding_amount
                FROM receivable
                JOIN sales_order ON sales_order.id = receivable.sales_order_id
                JOIN customer ON customer.id = receivable.customer_id
                LEFT JOIN paid ON paid.receivable_id = receivable.id
                WHERE (:customerId IS NULL OR receivable.customer_id = :customerId)
                  AND (:responsibleSalesUserId IS NULL
                       OR sales_order.responsible_sales_user_id = :responsibleSalesUserId)
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 應收總額與未收餘額只算正式應收；待確認的草稿另外計算筆數與金額
     *
     * @param customerId             null 代表全部客戶
     * @param responsibleSalesUserId null 代表全部業務
     * @return 金額合計
     */
    public ReceivableTotalsProjection findTotals(Long customerId, Long responsibleSalesUserId) {
        String sql = RECEIVABLE_BALANCES + """
                SELECT
                    COALESCE(SUM(CASE WHEN confirmed     THEN total_amount       END), 0) AS total_amount,
                    COALESCE(SUM(CASE WHEN confirmed     THEN outstanding_amount END), 0) AS outstanding_amount,
                    COUNT(CASE WHEN NOT confirmed THEN 1 END)                             AS pending_count,
                    COALESCE(SUM(CASE WHEN NOT confirmed THEN total_amount       END), 0) AS pending_amount
                FROM receivable_balance
                """;

        return jdbcTemplate.queryForObject(
                sql,
                filters(customerId, responsibleSalesUserId),
                (rs, rowNum) -> new ReceivableTotalsProjection(
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("outstanding_amount"),
                        rs.getInt("pending_count"),
                        rs.getBigDecimal("pending_amount")
                )
        );
    }

    /**
     * 還沒收清的應收，包含待確認的草稿；依到期日排序
     *
     * @param customerId             null 代表全部客戶
     * @param responsibleSalesUserId null 代表全部業務
     * @return 每筆應收的未收餘額與到期日
     */
    public List<OpenReceivableProjection> findOpenReceivables(Long customerId, Long responsibleSalesUserId) {

        String sql = RECEIVABLE_BALANCES + """
                SELECT *
                FROM receivable_balance
                WHERE outstanding_amount > 0
                ORDER BY due_date, id
                """;

        return jdbcTemplate.query(
                sql,
                filters(customerId, responsibleSalesUserId),
                (rs, rowNum) -> new OpenReceivableProjection(
                        rs.getLong("id"),
                        rs.getString("receivable_number"),
                        rs.getString("status"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getLong("sales_order_id"),
                        rs.getString("so_number"),
                        rs.getLong("delivery_order_id"),
                        rs.getString("do_number"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getBigDecimal("outstanding_amount")
                )
        );
    }

    private static MapSqlParameterSource filters(Long customerId, Long responsibleSalesUserId) {
        return new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("responsibleSalesUserId", responsibleSalesUserId);
    }
}
