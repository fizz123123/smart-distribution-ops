package com.smartdistribution.purchasing.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * 收貨單：建立即過帳，沒有草稿與狀態，過帳後不修改也不沖銷；一張收貨單只對一張 PO
 */
@Getter
@Entity
@Table(name = "goods_receipt")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceipt extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", unique = true, length = 20)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    //實際到貨日，由倉管填寫；過帳時間另外記在 postedAt
    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "posted_by", nullable = false)
    private Long postedBy;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public GoodsReceipt(PurchaseOrder purchaseOrder, LocalDate receivedDate, Long postedBy, Instant postedAt) {
        this.purchaseOrder = purchaseOrder;
        this.receivedDate = receivedDate;
        this.postedBy = postedBy;
        this.postedAt = postedAt;
    }

    public void assignNumber() {
        this.receiptNumber = "GR-%06d".formatted(id);
    }
}
