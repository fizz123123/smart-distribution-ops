package com.smartdistribution.inventory.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 庫存預留：每筆SO明細對應一筆預留資料
 * open_qty 必須等於該預留所有事件的 RESERVE － CONSUME － RELEASE
 * 跨模組只存 SO 的 ID；SO 編號是建立時的快照，讓庫存畫面與錯誤訊息不必查銷售模組
 */
@Getter
@Entity
@Table(
        name = "inventory_reservation",
        indexes = @Index(name = "idx_inventory_reservation_item", columnList = "company_item_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservation extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_order_line_id", nullable = false, unique = true)
    private Long salesOrderLineId;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "sales_order_number", nullable = false, length = 20)
    private String salesOrderNumber;

    @Column(name = "company_item_id", nullable = false)
    private Long companyItemId;

    @Column(name = "open_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal openQty;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryReservation(
            Long salesOrderLineId,
            Long salesOrderId,
            String salesOrderNumber,
            Long companyItemId
    ) {
        this.salesOrderLineId = salesOrderLineId;
        this.salesOrderId = salesOrderId;
        this.salesOrderNumber = salesOrderNumber;
        this.companyItemId = companyItemId;
        this.openQty = BigDecimal.ZERO;
    }

    public void increase(BigDecimal quantity) {
        this.openQty = openQty.add(quantity);
    }

    public void decrease(BigDecimal quantity) {
        if (openQty.compareTo(quantity) < 0) {
            throw new IllegalStateException("預留量不可小於 0");
        }

        this.openQty = openQty.subtract(quantity);
    }
}
