package com.smartdistribution.analytics.shortage.service;

/**
 * 料號的缺貨風險原因，可以同時有好幾個
 * 前兩種是庫存面：料號整體還夠不夠；後四種是供應面：已經接下的 SO 能不能如期備齊
 * 回應分成 stockRisks、supplyRisks 兩組，由 ShortageRules 的兩個方法各自產生
 */
public enum ShortageRisk {
    BELOW_SAFETY_STOCK,     //可用量低於安全庫存
    BELOW_REORDER_POINT,    //可用量加採購在途低於建議再訂購點：現在不下單，下一批到之前就會斷貨
    UNPLANNED_DEMAND,       //有已核准 SO 明細還有尚待規劃的數量
    PO_NOT_APPROVED,        //有 SO 明細的缺料只排進還沒核准的 PO
    ARRIVAL_AFTER_DELIVERY, //有關聯的已核准 PO 預計晚於 SO 明細的交期到貨
    ARRIVAL_OVERDUE         //有已核准 PO 已經過了預計到貨日還沒收齊，含一般補庫 PO
}
