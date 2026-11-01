package com.smartdistribution.purchasing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 收貨明細：數量使用 PO 明細的單位；實收 ＝ 合格 ＋ 損壞 ＋ 規格不符
 * 只有合格數量入庫，損壞與規格不符只記錄，不入庫、也不算收齊
 */
@Getter
@Entity
@Table(name = "goods_receipt_line")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @Column(name = "received_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "accepted_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal acceptedQty;

    @Column(name = "damaged_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal damagedQty;

    @Column(name = "nonconforming_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal nonconformingQty;

    //合格數量換算成庫存基準單位，入庫與補足預留都用它
    @Column(name = "accepted_base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal acceptedBaseQty;

    //有損壞或規格不符時必填
    @Column(name = "exception_reason", length = 500)
    private String exceptionReason;

    public GoodsReceiptLine(
            GoodsReceipt goodsReceipt,
            PurchaseOrderLine purchaseOrderLine,
            Quantities quantities,
            BigDecimal acceptedBaseQty,
            String exceptionReason
    ) {
        this.goodsReceipt = goodsReceipt;
        this.purchaseOrderLine = purchaseOrderLine;
        this.receivedQty = quantities.received();
        this.acceptedQty = quantities.accepted();
        this.damagedQty = quantities.damaged();
        this.nonconformingQty = quantities.nonconforming();
        this.acceptedBaseQty = acceptedBaseQty;
        this.exceptionReason = normalizeOptional(exceptionReason);
    }

    //空白或只有空格的原因視為沒填，有內容就去掉前後空白
    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 一筆收貨的四個數量，以 PO 明細的單位表示
     */
    public record Quantities(
            BigDecimal received,
            BigDecimal accepted,
            BigDecimal damaged,
            BigDecimal nonconforming
    ) {
    }
}
