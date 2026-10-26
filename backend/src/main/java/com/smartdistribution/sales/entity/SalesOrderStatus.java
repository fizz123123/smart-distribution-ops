package com.smartdistribution.sales.entity;

public enum SalesOrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    RETURNED,
    APPROVED,   //已核准並依可用量預留；之後由 DO 出貨或餘量取消
    CLOSED,     //每筆明細的有效訂購量都已出貨或取消
    CANCELLED   //放棄的草稿，只能從 DRAFT / RETURNED 進入
}
