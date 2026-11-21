package com.smartdistribution.analytics.shortage.repository;

import com.smartdistribution.analytics.shortage.repository.projection.CustomerNameProjection;
import com.smartdistribution.analytics.shortage.repository.projection.ItemStockProjection;
import com.smartdistribution.analytics.shortage.repository.projection.OpenPurchaseLineProjection;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 缺貨分析的唯讀查詢：庫存、安全庫存、出貨量、採購週期與還沒結的 PO
 * 尚待規劃不在這裡算，由銷售模組的 SalesOrderSupplyService 提供；全部數量都是庫存基準單位
 */
@Repository
@RequiredArgsConstructor
public class ShortageAnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * shipped：期間內已過帳 DO 的實際出貨量，依料號加總
     * last_receipt：每筆 PO 明細最後一次有合格數量的收貨日
     * lead_time：收齊（COMPLETED）的 PO 明細，從核准日到最後一次合格收貨的平均天數；短收結案、取消的不算
     *
     * @param usageFrom   出貨量從這天算起（今天往前 29 天）
     * @param historyFrom 採購週期只看最後收貨日在這天以後的明細（六個月前）
     * @return 每個料號一筆，依料號代碼排序
     */
    public List<ItemStockProjection> findItemStocks(
            Long companyItemId,
            LocalDate usageFrom,
            LocalDate today,
            LocalDate historyFrom
    ) {
        String sql = """
                WITH shipped AS (
                    SELECT delivery_order_line.company_item_id,
                           SUM(delivery_order_line.actual_base_qty) AS quantity
                    FROM delivery_order_line
                    JOIN delivery_order ON delivery_order.id = delivery_order_line.delivery_order_id
                    WHERE delivery_order.status = 'POSTED'
                      AND delivery_order.actual_shipment_date BETWEEN :usageFrom AND :today
                    GROUP BY delivery_order_line.company_item_id
                ),
                last_receipt AS (
                    SELECT goods_receipt_line.purchase_order_line_id,
                           MAX(goods_receipt.received_date) AS received_date
                    FROM goods_receipt_line
                    JOIN goods_receipt ON goods_receipt.id = goods_receipt_line.goods_receipt_id
                    WHERE goods_receipt_line.accepted_base_qty > 0
                    GROUP BY goods_receipt_line.purchase_order_line_id
                ),
                lead_time AS (
                    SELECT purchase_order_line.company_item_id,
                           ROUND(AVG(DATEDIFF(last_receipt.received_date, DATE(purchase_order.approved_at))), 1)
                               AS days
                    FROM purchase_order_line
                    JOIN purchase_order ON purchase_order.id = purchase_order_line.purchase_order_id
                    JOIN last_receipt ON last_receipt.purchase_order_line_id = purchase_order_line.id
                    WHERE purchase_order_line.status = 'COMPLETED'
                      AND last_receipt.received_date >= :historyFrom
                    GROUP BY purchase_order_line.company_item_id
                )
                SELECT company_item.id,
                       company_item.item_code,
                       company_item.name,
                       company_item.base_unit_code,
                       company_item.status = 'ACTIVE' AS active,
                       COALESCE(inventory_balance.on_hand_qty, 0) AS on_hand_qty,
                       COALESCE(inventory_balance.reserved_qty, 0) AS reserved_qty,
                       inventory_policy.safety_stock_base_qty,
                       COALESCE(shipped.quantity, 0) AS shipped_qty,
                       lead_time.days AS lead_time_days
                FROM company_item
                LEFT JOIN inventory_balance ON inventory_balance.company_item_id = company_item.id
                LEFT JOIN inventory_policy ON inventory_policy.company_item_id = company_item.id
                LEFT JOIN shipped ON shipped.company_item_id = company_item.id
                LEFT JOIN lead_time ON lead_time.company_item_id = company_item.id
                WHERE (:companyItemId IS NULL OR company_item.id = :companyItemId)
                ORDER BY company_item.item_code
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyItemId", companyItemId)
                .addValue("usageFrom", usageFrom)
                .addValue("today", today)
                .addValue("historyFrom", historyFrom);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ItemStockProjection(
                rs.getLong("id"),
                rs.getString("item_code"),
                rs.getString("name"),
                UnitOfMeasure.valueOf(rs.getString("base_unit_code")),
                rs.getBoolean("active"),
                rs.getBigDecimal("on_hand_qty"),
                rs.getBigDecimal("reserved_qty"),
                rs.getBigDecimal("safety_stock_base_qty"),
                rs.getBigDecimal("shipped_qty"),
                rs.getBigDecimal("lead_time_days")
        ));
    }

    /**
     * 還沒結的 PO 明細：未核准的（已規劃未下單）與已核准未收齊的（採購在途），包含一般補庫的 PO
     *
     * @return 依預計到貨日排序
     */
    public List<OpenPurchaseLineProjection> findOpenPurchaseLines(Long companyItemId) {
        String sql = """
                SELECT purchase_order_line.company_item_id,
                       purchase_order.id AS purchase_order_id,
                       purchase_order.po_number,
                       purchase_order.status,
                       purchase_order.expected_arrival_date,
                       purchase_order_line.base_qty
                           - purchase_order_line.accepted_base_qty
                           - purchase_order_line.cancelled_base_qty AS open_qty
                FROM purchase_order_line
                JOIN purchase_order ON purchase_order.id = purchase_order_line.purchase_order_id
                WHERE purchase_order.status IN (
                          'DRAFT', 'PENDING_APPROVAL', 'RETURNED', 'APPROVED', 'PARTIALLY_RECEIVED'
                      )
                  AND purchase_order_line.status = 'OPEN'
                  AND (:companyItemId IS NULL OR purchase_order_line.company_item_id = :companyItemId)
                ORDER BY purchase_order.expected_arrival_date, purchase_order.id, purchase_order_line.line_no
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("companyItemId", companyItemId),
                (rs, rowNum) -> new OpenPurchaseLineProjection(
                        rs.getLong("company_item_id"),
                        rs.getLong("purchase_order_id"),
                        rs.getString("po_number"),
                        rs.getString("status"),
                        rs.getObject("expected_arrival_date", LocalDate.class),
                        rs.getBigDecimal("open_qty")
                )
        );
    }

    /**
     * @return 客戶 ID → 代碼與名稱
     */
    public Map<Long, CustomerNameProjection> findCustomerNames(Collection<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                SELECT id, customer_code, name
                FROM customer
                WHERE id IN (:customerIds)
                """;

        return jdbcTemplate.query(
                        sql,
                        new MapSqlParameterSource("customerIds", customerIds),
                        (rs, rowNum) -> new CustomerNameProjection(
                                rs.getLong("id"),
                                rs.getString("customer_code"),
                                rs.getString("name")
                        )
                ).stream()
                .collect(Collectors.toMap(CustomerNameProjection::customerId, Function.identity()));
    }
}
