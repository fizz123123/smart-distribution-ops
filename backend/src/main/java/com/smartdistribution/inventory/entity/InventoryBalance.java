package com.smartdistribution.inventory.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 每個公司料號一筆的可出貨實體數量。料號建立時即建立，之後只由庫存服務更新（10 號文件）。
 * 本切片只建立，不提供異動。
 */
@Getter
@Entity
@Table(name = "inventory_balance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBalance extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_item_id", nullable = false, unique = true)
    private Long companyItemId;

    @Column(name = "on_hand_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal onHandQty;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryBalance(Long companyItemId) {
        this.companyItemId = companyItemId;
        this.onHandQty = BigDecimal.ZERO;
    }
}
