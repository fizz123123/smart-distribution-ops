package com.smartdistribution.analytics.shortage.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 料號的缺貨風險原因，可以同時有好幾個
 * 庫存面看的是料號整體還夠不夠；供應面看的是已經接下的 SO 能不能如期備齊
 */
@Getter
@RequiredArgsConstructor
public enum ShortageRisk {
    BELOW_SAFETY_STOCK(true),      //可用量低於安全庫存
    BELOW_REORDER_POINT(true),     //可用量加採購在途低於建議再訂購點：現在不下單，下一批到之前就會斷貨
    UNPLANNED_DEMAND(false),       //有已核准 SO 明細還有尚待規劃的數量
    PO_NOT_APPROVED(false),        //有 SO 明細的缺料只排進還沒核准的 PO
    ARRIVAL_AFTER_DELIVERY(false), //有關聯的已核准 PO 預計晚於 SO 明細的交期到貨
    ARRIVAL_OVERDUE(false);        //有已核准 PO 已經過了預計到貨日還沒收齊，含一般補庫 PO

    //true 是庫存面，false 是供應面；回應分兩組，前端分開顯示
    private final boolean stockSide;
}
