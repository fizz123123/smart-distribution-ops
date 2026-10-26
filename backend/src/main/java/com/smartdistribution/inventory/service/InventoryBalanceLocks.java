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
 * 會改變庫存或預留的操作，都要先鎖定料號的餘額列，再計算可用量；這個類別集中處理這兩件事
 * 目前用在庫存調整與 SO 核准預留，之後的餘量取消、入庫與出貨也會使用
 *
 * <p>計算結果要正確，必須同時滿足兩個前提：
 * <ol>
 *   <li>所有會新增或減少預留的操作，都先呼叫 {@link #lockInItemOrder}，同一料號因此只會有一個交易在計算</li>
 *   <li>資料庫使用 READ COMMITTED（見 application.properties），鎖定後的查詢才讀得到前一個交易剛提交的預留</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class InventoryBalanceLocks {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

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

    /**
     * 料號目前的有效預留總量
     * 必須在鎖定該料號之後呼叫，查到的數字才不會在計算期間被其他交易改變
     */
    public BigDecimal reservedQty(Long companyItemId) {
        return inventoryReservationRepository.sumOpenQtyByCompanyItemId(companyItemId);
    }

    /**
     * 可用庫存 ＝ 可出貨實體數量 － 有效預留總量
     * 傳入的餘額必須是 {@link #lockInItemOrder} 鎖定後取得的
     */
    public BigDecimal availableQty(InventoryBalance balance) {
        return balance.getOnHandQty().subtract(reservedQty(balance.getCompanyItemId()));
    }
}
