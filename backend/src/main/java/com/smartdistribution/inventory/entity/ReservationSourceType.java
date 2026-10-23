package com.smartdistribution.inventory.entity;

/**
 * 預留事件的來源；來源類型決定 source_id 指向哪一張資料表
 */
public enum ReservationSourceType {

    // source_id → approval_request（SO 核准申請）
    SO_APPROVAL,
    // source_id → sales_order_cancellation
    SO_CANCELLATION
}
