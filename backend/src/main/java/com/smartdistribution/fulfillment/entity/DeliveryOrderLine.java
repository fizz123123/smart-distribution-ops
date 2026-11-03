package com.smartdistribution.fulfillment.entity;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.sales.service.SalesOrderAmounts;
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
 * DO 明細：對應一筆 SO 明細；單位與換算倍率複製自 SO 明細送審時的快照
 * 料號 ID 也複製一份，過帳時直接交給庫存鎖定，不必再查 SO
 */
@Getter
@Entity
@Table(
        name = "delivery_order_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_order_line_no",
                columnNames = {"delivery_order_id", "line_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_order_id", nullable = false)
    private DeliveryOrder deliveryOrder;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "sales_order_line_id", nullable = false)
    private Long salesOrderLineId;

    @Column(name = "company_item_id", nullable = false)
    private Long companyItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(name = "conversion_factor", nullable = false, precision = 15, scale = 3)
    private BigDecimal conversionFactor;

    //業務通知的數量，以 SO 明細的單位表示
    @Column(name = "planned_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal plannedQuantity;

    @Column(name = "planned_base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal plannedBaseQty;

    //倉管完成揀貨時回報；0 代表這一筆這次不出
    @Column(name = "actual_quantity", precision = 15, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "actual_base_qty", precision = 15, scale = 3)
    private BigDecimal actualBaseQty;

    public DeliveryOrderLine(
            DeliveryOrder deliveryOrder,
            Integer lineNo,
            Long salesOrderLineId,
            Long companyItemId,
            UnitOfMeasure unit,
            BigDecimal conversionFactor,
            BigDecimal plannedQuantity
    ) {
        this.deliveryOrder = deliveryOrder;
        this.lineNo = lineNo;
        this.salesOrderLineId = salesOrderLineId;
        this.companyItemId = companyItemId;
        this.unit = unit;
        this.conversionFactor = conversionFactor;
        this.plannedQuantity = plannedQuantity;
        this.plannedBaseQty = SalesOrderAmounts.baseQty(plannedQuantity, conversionFactor);
    }

    //倉管回報實際找到多少；少於預計量的差額不使用預留，仍留給這張 SO
    public void recordActual(BigDecimal actualQuantity) {
        this.actualQuantity = actualQuantity;
        this.actualBaseQty = SalesOrderAmounts.baseQty(actualQuantity, conversionFactor);
    }

    //實際量是 0 的明細這次不出，不印在出貨單上，也不扣庫存
    public boolean isShipped() {
        return actualBaseQty != null && actualBaseQty.signum() > 0;
    }
}
