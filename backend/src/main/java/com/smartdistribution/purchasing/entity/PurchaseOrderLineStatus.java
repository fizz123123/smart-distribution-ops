package com.smartdistribution.purchasing.entity;

public enum PurchaseOrderLineStatus {
    OPEN,         //還有未結數量
    COMPLETED,    //合格入庫量已等於訂購量
    CLOSED_SHORT, //短收結案，剩下的數量不再等
    CANCELLED     //整張 PO 取消
}
