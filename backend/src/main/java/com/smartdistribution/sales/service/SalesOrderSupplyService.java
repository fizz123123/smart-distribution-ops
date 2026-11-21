package com.smartdistribution.sales.service;

import com.smartdistribution.sales.service.PurchaseSupplyProvider.LinkedPurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 其他模組查詢已核准 SO 明細供應狀態的入口，目前給儀表板用
 * 和 SO 詳情、缺料清單是同一套算法，尚待規劃不會有兩種答案；權限由呼叫的模組檢查
 */
public interface SalesOrderSupplyService {

    /**
     * @param companyItemId 公司料號篩選，null 代表全部
     * @return 已核准 SO 的每一筆明細與供應狀態，依交期排序
     */
    List<ApprovedLineSupply> findApprovedLineSupplies(Long companyItemId);

    /**
     * 一筆已核准 SO 明細的供應狀態，數量都是庫存基準單位
     *
     * @param deliveryDate         實際交期（送審快照）
     * @param remainingQty         剩餘需求 ＝ 訂購 － 已核准取消 － 已出貨
     * @param plannedNotOrderedQty 排進未核准 PO 的數量
     * @param inTransitQty         排進已核准 PO、還沒入庫的數量
     * @param unplannedQty         尚待規劃：剩餘需求扣掉預留、已規劃、在途後還缺的數量
     * @param purchaseOrders       關聯這一行的所有 PO，包含已取消、已結案的
     */
    record ApprovedLineSupply(
            Long salesOrderId,
            String soNumber,
            Long salesOrderLineId,
            Integer lineNo,
            Long customerId,
            Long responsibleSalesUserId,
            Long companyItemId,
            LocalDate deliveryDate,
            BigDecimal remainingQty,
            BigDecimal reservedQty,
            BigDecimal plannedNotOrderedQty,
            BigDecimal inTransitQty,
            BigDecimal unplannedQty,
            List<LinkedPurchaseOrder> purchaseOrders
    ) {
    }
}
