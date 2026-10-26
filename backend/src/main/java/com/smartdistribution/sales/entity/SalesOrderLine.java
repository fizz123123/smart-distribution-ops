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
 * SO 明細。草稿只存使用者輸入（客戶料號對應、數量、單位）；
 * 送審時寫入快照：公司料號、客戶料號、倍率、基準數量、價格來源、單價、小計與稅率。
 */
@Getter
@Entity
@Table(
        name = "sales_order_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_order_line_no",
                columnNames = {"sales_order_id", "line_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //同一模組的表頭用關聯，資料庫有外鍵
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "customer_item_mapping_id", nullable = false)
    private Long customerItemMappingId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 10)
    private UnitOfMeasure unit;

    // ===== 以下是送審快照 =====

    @Column(name = "company_item_id")
    private Long companyItemId;

    @Column(name = "customer_item_code", length = 50)
    private String customerItemCode;

    @Column(name = "conversion_factor", precision = 15, scale = 3)
    private BigDecimal conversionFactor;

    //庫存基準單位的數量，預留與出貨都用它
    @Column(name = "base_qty", precision = 15, scale = 3)
    private BigDecimal baseQty;

    @Column(name = "customer_price_id")
    private Long customerPriceId;

    //計價單位與訂購單位相同，仍分開保存，讓快照完整說明金額怎麼算
    @Column(name = "pricing_qty", precision = 15, scale = 3)
    private BigDecimal pricingQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_unit_code", length = 10)
    private UnitOfMeasure pricingUnit;

    @Column(name = "unit_price_excluding_tax", precision = 15, scale = 2)
    private BigDecimal unitPriceExcludingTax;

    @Column(name = "line_amount_excluding_tax", precision = 15, scale = 2)
    private BigDecimal lineAmountExcludingTax;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    public SalesOrderLine(
            SalesOrder salesOrder,
            int lineNo,
            Long customerItemMappingId,
            BigDecimal quantity,
            UnitOfMeasure unit
    ) {
        this.salesOrder = salesOrder;
        this.lineNo = lineNo;
        this.customerItemMappingId = customerItemMappingId;
        this.quantity = quantity;
        this.unit = unit;
    }

    public void applySnapshot(Snapshot snapshot) {
        this.companyItemId = snapshot.companyItemId();
        this.customerItemCode = snapshot.customerItemCode();
        this.conversionFactor = snapshot.conversionFactor();
        this.baseQty = snapshot.baseQty();
        this.customerPriceId = snapshot.customerPriceId();
        this.pricingQty = quantity;
        this.pricingUnit = unit;
        this.unitPriceExcludingTax = snapshot.unitPriceExcludingTax();
        this.lineAmountExcludingTax = snapshot.lineAmountExcludingTax();
        this.taxRate = snapshot.taxRate();
    }

    public record Snapshot(
            Long companyItemId,
            String customerItemCode,
            BigDecimal conversionFactor,
            BigDecimal baseQty,
            Long customerPriceId,
            BigDecimal unitPriceExcludingTax,
            BigDecimal lineAmountExcludingTax,
            BigDecimal taxRate
    ) {
    }
}
