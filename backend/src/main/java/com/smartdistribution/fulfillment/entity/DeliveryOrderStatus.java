package com.smartdistribution.fulfillment.entity;

public enum DeliveryOrderStatus {
    DRAFT,  //業務已通知出貨，等倉管揀貨
    READY,  //揀貨完成、出貨單已產生，等交給司機
    POSTED, //已交給司機並過帳，庫存已扣
    VOIDED  //撤回或作廢，不會再出貨
}
