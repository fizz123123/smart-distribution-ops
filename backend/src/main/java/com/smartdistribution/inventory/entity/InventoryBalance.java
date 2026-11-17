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
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

/**
 * 每個公司料號一筆的可出貨實體數量與已預留數量，料號建立時即建立，之後只由庫存服務更新
 * 所有會改變庫存或預留的操作都先鎖定這一列；鎖定讀取一定讀到最新已提交的值，
 * 所以可用量直接用這一列計算，不必再加總預留表
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

    //可出貨實體數量：倉庫實際有的數量，其中一部分可能已被預留；以料號基準單位保存，可能是小數
    @Column(name = "on_hand_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal onHandQty;

    //已預留數量：這個料號所有預留的 open_qty 加總，和預留表在同一個交易裡同步增減
    //可用庫存 ＝ 可出貨實體數量 － 已預留數量，不另外保存
    @ColumnDefault("0")
    @Column(name = "reserved_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal reservedQty;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryBalance(Long companyItemId) {
        this.companyItemId = companyItemId;
        this.onHandQty = BigDecimal.ZERO;
        this.reservedQty = BigDecimal.ZERO;
    }

    public BigDecimal availableQty() {
        return onHandQty.subtract(reservedQty);
    }

    /**
     * 增減可出貨實體數量
     * 業務規則（數量不足、會侵蝕預留）由呼叫端先檢查並回傳友善訊息，這裡是最後一道保護
     */
    public void change(BigDecimal quantityChange) {
        BigDecimal newQty = onHandQty.add(quantityChange);

        if (newQty.signum() < 0) {
            throw new IllegalStateException("可出貨實體數量不可小於 0");
        }
        if (newQty.compareTo(reservedQty) < 0) {
            throw new IllegalStateException("可出貨實體數量不可小於已預留數量");
        }

        this.onHandQty = newQty;
    }

    /**
     * 預留增加時呼叫，和 InventoryReservation.increase 在同一個交易裡成對使用
     */
    public void increaseReserved(BigDecimal quantity) {
        BigDecimal newQty = reservedQty.add(quantity);

        if (newQty.compareTo(onHandQty) > 0) {
            throw new IllegalStateException("已預留數量不可大於可出貨實體數量");
        }

        this.reservedQty = newQty;
    }

    /**
     * 預留釋放或出貨消耗時呼叫，和 InventoryReservation.decrease 在同一個交易裡成對使用
     */
    public void decreaseReserved(BigDecimal quantity) {
        BigDecimal newQty = reservedQty.subtract(quantity);

        if (newQty.signum() < 0) {
            throw new IllegalStateException("已預留數量不可小於 0");
        }

        this.reservedQty = newQty;
    }
}
