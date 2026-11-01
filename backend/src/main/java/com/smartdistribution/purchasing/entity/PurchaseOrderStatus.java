package com.smartdistribution.purchasing.entity;

public enum PurchaseOrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    RETURNED,
    APPROVED,           //已核准並產生正式 PDF，數量計入採購在途
    PARTIALLY_RECEIVED, //已有收貨，仍有未結數量
    CLOSED,             //全部收齊，或短收結案
    CANCELLED           //放棄的草稿，或取消尚未收貨的已核准 PO
}
