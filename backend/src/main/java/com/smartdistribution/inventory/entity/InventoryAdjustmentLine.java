package com.smartdistribution.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(
        name = "inventory_adjustment_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_adjustment_line_item",
                columnNames = {"adjustment_id", "company_item_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryAdjustmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private InventoryAdjustment adjustment;

    @Column(name = "company_item_id", nullable = false)
    private Long companyItemId;

    //以庫存基準單位輸入，增加為正、減少為負
    @Column(name = "quantity_change_base", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityChangeBase;

    public InventoryAdjustmentLine(
            InventoryAdjustment adjustment,
            Long companyItemId,
            BigDecimal quantityChangeBase
    ) {
        this.adjustment = adjustment;
        this.companyItemId = companyItemId;
        this.quantityChangeBase = quantityChangeBase;
    }
}
