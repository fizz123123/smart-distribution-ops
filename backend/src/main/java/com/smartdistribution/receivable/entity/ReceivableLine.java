package com.smartdistribution.receivable.entity;

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
 * 應收明細：一筆實際出貨的 DO 明細；數量、單價、小計都是建立當下的快照
 * 存下來之後，財務核對金額時不必再同時查 DO 與 SO
 */
@Getter
@Entity
@Table(
        name = "receivable_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_receivable_line_no",
                columnNames = {"receivable_id", "line_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivableLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receivable_id", nullable = false)
    private Receivable receivable;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "delivery_order_line_id", nullable = false)
    private Long deliveryOrderLineId;

    @Column(name = "sales_order_line_id", nullable = false)
    private Long salesOrderLineId;

    @Column(name = "company_item_id", nullable = false)
    private Long companyItemId;

    @Column(name = "customer_item_code", length = 50)
    private String customerItemCode;

    //實際出貨量，也就是計價數量
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(name = "unit_price_excluding_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceExcludingTax;

    @Column(name = "line_amount_excluding_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineAmountExcludingTax;

    public ReceivableLine(
            Receivable receivable,
            Integer lineNo,
            Long deliveryOrderLineId,
            Long salesOrderLineId,
            Long companyItemId,
            String customerItemCode,
            BigDecimal quantity,
            UnitOfMeasure unit,
            BigDecimal unitPriceExcludingTax,
            BigDecimal lineAmountExcludingTax
    ) {
        this.receivable = receivable;
        this.lineNo = lineNo;
        this.deliveryOrderLineId = deliveryOrderLineId;
        this.salesOrderLineId = salesOrderLineId;
        this.companyItemId = companyItemId;
        this.customerItemCode = customerItemCode;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPriceExcludingTax = unitPriceExcludingTax;
        this.lineAmountExcludingTax = lineAmountExcludingTax;
    }
}
