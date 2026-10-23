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
 * 每個公司料號對應一筆的可出貨實體數量。料號建立時即建立，之後只由庫存服務更新（10 號文件）。
 * 所有會改變庫存或預留的操作都先鎖定這一列，再重新計算可用量（10 號第 6 節）。
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

    //實際可用庫存數量 = 已預留數量 + 尚未被預留數量(系統不存，運算取得);
    //存料號的基本單位，可能是小數
    @Column(name = "on_hand_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal onHandQty;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryBalance(Long companyItemId) {
        this.companyItemId = companyItemId;
        this.onHandQty = BigDecimal.ZERO;
    }

    public void change(BigDecimal quantityChange) {
        BigDecimal newQty = onHandQty.add(quantityChange);

        if (newQty.signum() < 0) {
            throw new IllegalStateException("可出貨實體數量不可小於 0");
        }

        this.onHandQty = newQty;
    }
}
