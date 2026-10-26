package com.smartdistribution.sales.entity;

public enum SalesOrderCancellationStatus {
    DRAFT,
    PENDING_APPROVAL,
    RETURNED,
    APPROVED, //已核准：減少有效訂購量，必要時釋放預留
    WITHDRAWN //業務撤回不想送出的申請，只能從 DRAFT / RETURNED 進入
}
