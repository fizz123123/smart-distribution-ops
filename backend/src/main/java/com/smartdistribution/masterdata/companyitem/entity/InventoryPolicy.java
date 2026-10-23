package com.smartdistribution.masterdata.companyitem.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 安全庫存政策，與公司料號一對一，以庫存基準單位保存（D-034）。
 * 和料號分成兩張表，是因為修改的人不同（業務改料號、採購主管改安全庫存），
 * 各自有版本號，兩人同時修改時不會互相造成版本衝突。
 */
@Getter
@Entity
@Table(name = "inventory_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryPolicy extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 同一模組用關聯，資料庫有外鍵
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_item_id", nullable = false, unique = true)
    private CompanyItem companyItem;

    @Column(name = "safety_stock_base_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal safetyStockBaseQty;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryPolicy(CompanyItem companyItem) {
        this.companyItem = companyItem;
        this.safetyStockBaseQty = BigDecimal.ZERO;
    }

    public void changeSafetyStock(BigDecimal safetyStockBaseQty) {
        this.safetyStockBaseQty = safetyStockBaseQty;
    }
}
