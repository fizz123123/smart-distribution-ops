package com.smartdistribution.sales.entity;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.pricing.service.PriceSource;
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

    //業務在草稿填的單價，null 代表用底價；和送審快照分開存，退回重送時才知道哪些是業務自己填的
    @Column(name = "requested_unit_price", precision = 15, scale = 2)
    private BigDecimal requestedUnitPrice;

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

    //引用的價格：來源決定 price_id 指的是客戶價格還是標準售價
    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", length = 20)
    private PriceSource priceSource;

    @Column(name = "price_id")
    private Long priceId;

    //計價單位與訂購單位相同，仍分開保存，讓快照完整說明金額怎麼算
    @Column(name = "pricing_qty", precision = 15, scale = 3)
    private BigDecimal pricingQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_unit_code", length = 10)
    private UnitOfMeasure pricingUnit;

    //送審當下取價服務找到的價格，也就是底價
    @Column(name = "list_unit_price", precision = 15, scale = 2)
    private BigDecimal listUnitPrice;

    //實際成交價，金額與應收都照它計算
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
            UnitOfMeasure unit,
            BigDecimal requestedUnitPrice
    ) {
        this.salesOrder = salesOrder;
        this.lineNo = lineNo;
        this.customerItemMappingId = customerItemMappingId;
        this.quantity = quantity;
        this.unit = unit;
        this.requestedUnitPrice = requestedUnitPrice;
    }

    /**
     * 成交價：業務有填就用業務填的，沒填就用底價
     *
     * @param listUnitPrice 底價，找不到價格時為 null
     * @return 成交價
     */
    public BigDecimal dealUnitPrice(BigDecimal listUnitPrice) {
        return requestedUnitPrice != null ? requestedUnitPrice : listUnitPrice;
    }

    /**
     * 業務填的單價是否低於底價；只限制下限，往上調不限
     *
     * @param listUnitPrice 底價
     * @return 低於底價時為 true
     */
    public boolean isBelowListPrice(BigDecimal listUnitPrice) {
        return requestedUnitPrice != null && requestedUnitPrice.compareTo(listUnitPrice) < 0;
    }

    public void applySnapshot(Snapshot snapshot) {
        this.companyItemId = snapshot.companyItemId();
        this.customerItemCode = snapshot.customerItemCode();
        this.conversionFactor = snapshot.conversionFactor();
        this.baseQty = snapshot.baseQty();
        this.priceSource = snapshot.priceSource();
        this.priceId = snapshot.priceId();
        this.listUnitPrice = snapshot.listUnitPrice();
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
            PriceSource priceSource,
            Long priceId,
            BigDecimal listUnitPrice,
            BigDecimal unitPriceExcludingTax,
            BigDecimal lineAmountExcludingTax,
            BigDecimal taxRate
    ) {
    }
}
