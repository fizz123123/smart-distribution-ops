package com.smartdistribution.inventory.service;

import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.repository.InventoryBalanceRepository;
import com.smartdistribution.inventory.repository.InventoryReservationRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 庫存調整、SO 核准預留、餘量取消都要先鎖定庫存餘額列再計算可用量，放在這裡共用。
 * 必須在呼叫端的交易中使用，鎖會保留到交易結束。
 */
@Component
@RequiredArgsConstructor
public class InventoryBalanceLocks {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    /**
     * 依公司料號 ID 由小到大逐一鎖定（10 號第 6 節）。
     * 兩個交易都依相同順序取鎖，就不會發生 A 等 B、B 又等 A 的死鎖。
     */
    public Map<Long, InventoryBalance> lockInItemOrder(Collection<Long> companyItemIds) {
        Map<Long, InventoryBalance> balances = new TreeMap<>();

        for (Long companyItemId : new TreeSet<>(companyItemIds)) {
            InventoryBalance balance = inventoryBalanceRepository
                    .findByCompanyItemIdForUpdate(companyItemId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND));
            balances.put(companyItemId, balance);
        }

        return balances;
    }

    // 有效預留總量；鎖定餘額列之後再查，才是不會被其他交易改變的數字
    public BigDecimal reservedQty(Long companyItemId) {
        return inventoryReservationRepository.sumOpenQtyByCompanyItemId(companyItemId);
    }

    // 可用庫存 ＝ 可出貨實體數量 － 有效預留總量
    public BigDecimal availableQty(InventoryBalance balance) {
        return balance.getOnHandQty().subtract(reservedQty(balance.getCompanyItemId()));
    }
}
