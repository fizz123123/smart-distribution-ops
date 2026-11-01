package com.smartdistribution.purchasing.entity;

import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.purchasing.service.PurchaseOrderAmounts;
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
 * PO 明細：
 * - sales_order_line_id 有值就是關聯 SO 的缺料採購，空的就是一般補庫，不另存用途欄位單價以訂購單位計
 * - 基準數量依當下的換算倍率算出，送審時會用最新的倍率重算一次
 */
@Getter
@Entity
@Table(
        name = "purchase_order_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_order_line_no",
                columnNames = {"purchase_order_id", "line_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "company_item_id", nullable = false)
    private Long companyItemId;

    //關聯的 SO 明細；屬於銷售模組，只存 ID
    @Column(name = "sales_order_line_id")
    private Long salesOrderLineId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(name = "conversion_factor", nullable = false, precision = 15, scale = 3)
    private BigDecimal conversionFactor;

    //庫存基準單位的數量，規劃量、在途量與入庫都用它
    @Column(name = "base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal baseQty;

    //草稿可以先不填，送審時必填
    @Column(name = "unit_price_excluding_tax", precision = 15, scale = 2)
    private BigDecimal unitPriceExcludingTax;

    @Column(name = "line_amount_excluding_tax", precision = 15, scale = 2)
    private BigDecimal lineAmountExcludingTax;

    //累計合格入庫的基準數量；每一次收貨的明細另外保存在 goods_receipt_line
    @Column(name = "accepted_base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal acceptedBaseQty;

    //取消或短收結案而不再等的基準數量
    @Column(name = "cancelled_base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal cancelledBaseQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderLineStatus status;

    public PurchaseOrderLine(
            PurchaseOrder purchaseOrder,
            int lineNo,
            Long companyItemId,
            Long salesOrderLineId,
            BigDecimal quantity,
            UnitOfMeasure unit,
            BigDecimal conversionFactor,
            BigDecimal unitPriceExcludingTax
    ) {
        this.purchaseOrder = purchaseOrder;
        this.lineNo = lineNo;
        this.companyItemId = companyItemId;
        this.salesOrderLineId = salesOrderLineId;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPriceExcludingTax = unitPriceExcludingTax;
        this.acceptedBaseQty = BigDecimal.ZERO;
        this.cancelledBaseQty = BigDecimal.ZERO;
        this.status = PurchaseOrderLineStatus.OPEN;
        applyConversion(conversionFactor);
    }

    public boolean isLinkedToSalesOrder() {
        return salesOrderLineId != null;
    }

    public boolean isOpen() {
        return status == PurchaseOrderLineStatus.OPEN;
    }

    //未結量 ＝ 訂購量 － 累計合格入庫 － 不再等的數量（基準單位）
    public BigDecimal openBaseQty() {
        return baseQty.subtract(acceptedBaseQty).subtract(cancelledBaseQty);
    }

    //合格入庫後累加；累計合格量達到訂購量時明細完成。損壞與規格不符不算，未結量會留著等補貨
    public void receive(BigDecimal acceptedBaseQty) {
        this.acceptedBaseQty = this.acceptedBaseQty.add(acceptedBaseQty);

        if (openBaseQty().signum() <= 0) {
            this.status = PurchaseOrderLineStatus.COMPLETED;
        }
    }

    //整張 PO 取消：全部數量都不再等
    public void cancel() {
        this.cancelledBaseQty = baseQty;
        this.status = PurchaseOrderLineStatus.CANCELLED;
    }

    //短收結案：還沒收齊的明細，剩下的未結量轉為不再等；已完成的明細不變
    public void closeShort() {
        if (!isOpen()) {
            return;
        }

        this.cancelledBaseQty = cancelledBaseQty.add(openBaseQty());
        this.status = PurchaseOrderLineStatus.CLOSED_SHORT;
    }

    //送審時用最新的換算倍率重算基準數量與小計
    public void applyConversion(BigDecimal conversionFactor) {
        this.conversionFactor = conversionFactor;
        this.baseQty = PurchaseOrderAmounts.baseQty(quantity, conversionFactor);
        this.lineAmountExcludingTax = unitPriceExcludingTax == null
                ? null
                : PurchaseOrderAmounts.lineAmount(quantity, unitPriceExcludingTax);
    }
}
