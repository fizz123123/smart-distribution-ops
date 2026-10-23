package com.smartdistribution.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 實體庫存異動，只新增、不修改;
 * 同一料號所有異動的 quantity_change 加總必須等於 inventory_balance.on_hand_qty
 */
@Getter
@Entity
@Table(
        name = "inventory_transaction",
        indexes = @Index(name = "idx_inventory_transaction_item", columnList = "company_item_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_item_id", nullable = false)
    private Long companyItemId;

    //以庫存基準單位保存，增加為正、減少為負
    @Column(name = "quantity_change", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private InventoryTransactionType transactionType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public InventoryTransaction(
            Long companyItemId,
            BigDecimal quantityChange,
            InventoryTransactionType transactionType,
            Long sourceId,
            Long actorId,
            Instant occurredAt
    ) {
        this.companyItemId = companyItemId;
        this.quantityChange = quantityChange;
        this.transactionType = transactionType;
        this.sourceId = sourceId;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }
}
