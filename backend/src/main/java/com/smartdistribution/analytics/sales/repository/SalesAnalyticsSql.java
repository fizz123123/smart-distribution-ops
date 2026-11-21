package com.smartdistribution.analytics.sales.repository;

import com.smartdistribution.analytics.common.repository.AnalyticsFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;

/**
 * 接單與營收共用的 SQL 片段：每種資料先用一段 CTE 整理成「一列一筆明細」，並套用客戶、料號、負責業務的篩選
 * 合計（SalesAnalyticsRepository）和來源明細（SalesSourceRepository）讀同一段 CTE，明細加起來一定等於合計
 * 欄位統一叫 business_date、amount；日期一律用 DATE(...) 和 LocalDate 比較，不傳 Instant，避免時區換算差 8 小時
 */
final class SalesAnalyticsSql {

    //營收：每一筆應收明細，日期是 DO 實際出貨日；金額在 DO 過帳時已經算好
    static final String REVENUE_LINES = """
            WITH revenue_line AS (
                SELECT receivable_line.id AS line_id,
                       receivable.actual_shipment_date AS business_date,
                       receivable.id AS receivable_id,
                       receivable.receivable_number,
                       receivable.delivery_order_id,
                       receivable.do_number,
                       receivable.sales_order_id,
                       receivable.so_number,
                       receivable.customer_id,
                       receivable_line.company_item_id,
                       receivable_line.quantity,
                       receivable_line.unit_code,
                       receivable_line.unit_price_excluding_tax AS unit_price,
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
    static final String NEW_ORDER_LINES = """
            WITH new_order_line AS (
                SELECT DATE(sales_order.approved_at) AS business_date,
                       sales_order.id AS sales_order_id,
                       sales_order.so_number,
                       sales_order.customer_id,
                       sales_order.responsible_sales_user_id,
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
    static final String CANCELLATION_LINES = """
            WITH cancellation_line AS (
                SELECT cancel_line.id AS line_id,
                       DATE(cancellation.approved_at) AS business_date,
                       cancellation.id AS cancellation_id,
                       cancellation.cancellation_number,
                       sales_order.id AS sales_order_id,
                       sales_order.so_number,
                       sales_order.customer_id,
                       sales_order_line.company_item_id,
                       cancel_line.quantity,
                       cancel_line.unit_code,
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
    static final String SHIPPED_LINES = """
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
    static final String BACKLOG_LINES = """
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

    private SalesAnalyticsSql() {
    }

    //篩選參數；名稱要和上面 CTE 裡的 :customerId、:companyItemId、:responsibleSalesUserId 一致
    static MapSqlParameterSource scope(AnalyticsFilter filter) {
        return new MapSqlParameterSource()
                .addValue("customerId", filter.customerId())
                .addValue("companyItemId", filter.companyItemId())
                .addValue("responsibleSalesUserId", filter.responsibleSalesUserId());
    }

    //篩選參數加上期間
    static MapSqlParameterSource params(AnalyticsFilter filter, LocalDate from, LocalDate to) {
        return scope(filter)
                .addValue("from", from)
                .addValue("to", to);
    }
}
