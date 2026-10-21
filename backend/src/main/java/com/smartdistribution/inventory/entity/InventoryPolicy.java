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
 * 安全庫存政策，與公司料號一對一，以庫存基準單位保存（D-034）。
 * 只存公司料號 ID，不建立 JPA 關聯，庫存模組因此不依賴主檔模組（D-047）。
 */
@Getter
@Entity
@Table(name = "inventory_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryPolicy extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_item_id", nullable = false, unique = true)
    private Long companyItemId;

    @Column(name = "safety_stock_base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal safetyStockBaseQty;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryPolicy(Long companyItemId) {
        this.companyItemId = companyItemId;
        this.safetyStockBaseQty = BigDecimal.ZERO;
    }

    public void changeSafetyStock(BigDecimal safetyStockBaseQty) {
        this.safetyStockBaseQty = safetyStockBaseQty;
    }
}
