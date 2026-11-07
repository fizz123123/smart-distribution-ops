package com.smartdistribution.receivable.entity;

public enum PaymentStatus {
    CONFIRMED, //已入帳，核銷有效
    VOIDED     //財務主管作廢，核銷失效；保留紀錄不刪除
}
