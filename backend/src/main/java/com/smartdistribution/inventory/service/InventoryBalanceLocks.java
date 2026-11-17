package com.smartdistribution.inventory.service;

import com.smartdistribution.inventory.entity.InventoryBalance;
import com.smartdistribution.inventory.repository.InventoryBalanceRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 所有會改變庫存或預留的操作（SO 核准、餘量取消、收貨、出貨、庫存調整），都要先用這裡鎖定料號的餘額列
 * 同一料號因此只會有一個交易在計算；已預留數量也存在同一列，鎖定讀取一定讀到最新已提交的值，
 * 所以正確性不依賴資料庫的交易隔離層級
 */
@Component
@RequiredArgsConstructor
public class InventoryBalanceLocks {

    private final InventoryBalanceRepository inventoryBalanceRepository;

    /**
     * 以 SELECT … FOR UPDATE 鎖定料號的餘額列，回傳「料號 ID → 已鎖定的餘額」
     * 一律依料號 ID 由小到大取鎖：所有交易的取鎖順序相同，就不會出現 A 等 B、B 又等 A 的死鎖
     * 傳入重複的料號 ID 時只會鎖定一次
     *
     * @throws BusinessException 料號不存在時（每個料號建立時都會建立餘額列）
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
}
