package com.smartdistribution.inventory.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 其他模組使用庫存模組的入口，庫存與預留只能透過這裡異動，其他模組不直接存取庫存資料表
 */
public interface InventoryService {

    /**
     * 公司料號建立時呼叫，在同一個交易中建立庫存餘額列（數量設為 0）
     */
    void initializeForCompanyItem(Long companyItemId);

    /**
     * SO 核准時呼叫：鎖定相關料號的餘額列，依可用量為每筆明細建立預留（可能是 0）
     * 同一料號有多筆明細時依清單順序分配，呼叫端應依行號排序
     *
     * @param approvalRequestId 預留事件的來源（SO 核准申請）
     */
    void reserveForSalesOrder(
            List<ReservationLine> lines,
            Long approvalRequestId,
            Long actorId
    );

    /**
     * 餘量取消核准時呼叫：鎖定相關料號的餘額列後，把預留超過剩餘需求的部分釋放，並寫入 RELEASE 事件
     * 預留沒有超過剩餘需求時不會變動，也不寫事件（先取消未預留的部分，Q5）
     *
     * @param cancellationId 預留事件的來源（餘量取消申請）
     */
    void releaseForCancellation(
            List<ReservationDemand> demands,
            Long cancellationId,
            Long actorId
    );

    /**
     * @return SO 明細 ID → 目前有效預留；沒有預留列的明細不會出現在結果中
     */
    Map<Long, BigDecimal> findOpenQtyBySalesOrderLines(Collection<Long> salesOrderLineIds);

    /**
     * @return 公司料號 ID → 目前可用庫存，不鎖定，只供畫面提示使用；真正預留時會重新計算
     */
    Map<Long, BigDecimal> findAvailableQty(Collection<Long> companyItemIds);
}
