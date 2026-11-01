package com.smartdistribution.inventory.entity;

/**
 * 預留事件的來源；來源類型決定 source_id 指向哪一張資料表
 */
public enum ReservationSourceType {
    SO_APPROVAL,    // source_id → approval_request
    SO_CANCELLATION, // source_id → sales_order_cancellation
    PO_RECEIPT       // source_id → goods_receipt_line（關聯 PO 入庫時補足預留）
}
