package com.smartdistribution.sales.entity;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 取消申請的一筆明細：要取消哪一筆 SO 明細、取消多少
 * 單位固定是 SO 明細的訂購單位，依 SO 送審時的倍率快照換算成基準數量
 */
@Getter
@Entity
@Table(
        name = "sales_order_cancellation_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_order_cancellation_line",
                columnNames = {"cancellation_id", "sales_order_line_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrderCancellationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancellation_id", nullable = false)
    private SalesOrderCancellation cancellation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_line_id", nullable = false)
    private SalesOrderLine salesOrderLine;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(name = "base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal baseQty;

    public SalesOrderCancellationLine(
            SalesOrderCancellation cancellation,
            SalesOrderLine salesOrderLine,
            BigDecimal quantity,
            BigDecimal baseQty
    ) {
        this.cancellation = cancellation;
        this.salesOrderLine = salesOrderLine;
        this.quantity = quantity;
        this.unit = salesOrderLine.getUnit();
        this.baseQty = baseQty;
    }
}
