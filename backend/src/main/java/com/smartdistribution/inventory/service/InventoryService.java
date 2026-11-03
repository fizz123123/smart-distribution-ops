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
     * PO 驗收時呼叫：鎖定相關料號的餘額列，合格數量入庫並寫入 GOODS_RECEIPT 異動；
     * 關聯 SO 的明細再補足預留，寫入 RESERVE 事件（來源 PO_RECEIPT）
     *
     * <p>補足量 ＝ min(合格量, max(0, 剩餘需求 － 目前預留))，其餘合格數量成為一般可用庫存；
     * 一般補庫的入庫不會改配給任何缺料 SO
     */
    void receiveForPurchaseOrder(List<ReceiptLine> lines, Long actorId);

    /**
     * DO 過帳時呼叫：鎖定相關料號的餘額列，實體數量與預留同時減少實際出貨量，
     * 寫入 DELIVERY 異動與 CONSUME 事件（來源 DO_POSTING）
     *
     * <p>預留不足時拒絕；實體數量一定 ≥ 預留總量，所以預留夠就代表實體夠
     */
    void consumeForDelivery(List<DeliveryLine> lines, Long actorId);

    /**
     * @return SO 明細 ID → 目前有效預留；沒有預留列的明細不會出現在結果中
     */
    Map<Long, BigDecimal> findOpenQtyBySalesOrderLines(Collection<Long> salesOrderLineIds);

    /**
     * @return 公司料號 ID → 目前可用庫存，不鎖定，只供畫面提示使用；真正預留時會重新計算
     */
    Map<Long, BigDecimal> findAvailableQty(Collection<Long> companyItemIds);

    /**
     * SO 核准時，銷售模組交給庫存模組的「一筆明細要預留多少」，
     * 是 {@link InventoryService#reserveForSalesOrder} 的輸入
     *
     * <p>銷售模組本來就會呼叫庫存模組，如果庫存再依賴銷售的 entity，
     * 兩個模組就互相依賴，改由銷售模組轉成這個 record，
     * 庫存只拿到預留需要的 5 個值，也不會不小心用到 SO 的其他欄位或關聯
     *
     * <p>同一料號有多筆明細時，庫存依清單順序分配可用量，所以呼叫端要依行號排序
     *
     * @param salesOrderLineId 預留屬於哪一筆 SO 明細；每筆明細最多一筆預留
     * @param salesOrderId     存在預留上，供查詢與追溯
     * @param salesOrderNumber SO 編號快照，讓庫存畫面與錯誤訊息不必回頭查銷售模組
     * @param companyItemId    要鎖定與計算可用量的料號
     * @param baseQty          要求預留的數量，以庫存基準單位表示
     */
    record ReservationLine(
            Long salesOrderLineId,
            Long salesOrderId,
            String salesOrderNumber,
            Long companyItemId,
            BigDecimal baseQty
    ) {
    }

    /**
     * 餘量取消核准時，銷售模組告訴庫存模組「這筆 SO 明細取消後還需要多少」，
     * 是 {@link InventoryService#releaseForCancellation} 的輸入
     *
     * <p>只傳「取消後的剩餘需求」而不是「取消量」：先取消未預留的部分，
     * 預留超過剩餘需求時才釋放（Q5），這個判斷交給庫存模組在鎖定後計算
     *
     * @param salesOrderLineId 要調整預留的 SO 明細
     * @param companyItemId    要鎖定的料號
     * @param remainingQty     取消後仍需供應的基準數量（有效訂購量 － 已出貨量）
     */
    record ReservationDemand(
            Long salesOrderLineId,
            Long companyItemId,
            BigDecimal remainingQty
    ) {
    }

    /**
     * PO 驗收時，採購模組交給庫存模組的「一筆合格入庫」，
     * 是 {@link InventoryService#receiveForPurchaseOrder} 的輸入
     *
     * @param goodsReceiptLineId 入庫異動與預留事件的來源
     * @param companyItemId      要鎖定與增加庫存的料號
     * @param acceptedBaseQty    合格數量（基準單位），只有合格的才入庫
     * @param salesOrderLineId   關聯 PO 才有值：要補足預留的 SO 明細；一般補庫為 null
     * @param remainingQty       關聯 SO 明細的剩餘需求（有效訂購量 － 已出貨量）；一般補庫為 0
     */
    record ReceiptLine(
            Long goodsReceiptLineId,
            Long companyItemId,
            BigDecimal acceptedBaseQty,
            Long salesOrderLineId,
            BigDecimal remainingQty
    ) {
    }

    /**
     * DO 過帳時，出貨模組交給庫存模組的「一筆實際出貨」，
     * 是 {@link InventoryService#consumeForDelivery} 的輸入
     *
     * @param deliveryOrderLineId 出庫異動與預留事件的來源
     * @param companyItemId       要鎖定與扣減的料號
     * @param salesOrderLineId    要使用預留的 SO 明細
     * @param shippedBaseQty      實際出貨量（基準單位），大於 0
     */
    record DeliveryLine(
            Long deliveryOrderLineId,
            Long companyItemId,
            Long salesOrderLineId,
            BigDecimal shippedBaseQty
    ) {
    }
}
