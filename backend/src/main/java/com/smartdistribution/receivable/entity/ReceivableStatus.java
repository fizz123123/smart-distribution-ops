package com.smartdistribution.receivable.entity;

public enum ReceivableStatus {
    PENDING_CONFIRMATION, //DO 過帳後自動建立，等財務填發票並確認
    OPEN,                 //已確認，還沒有收款
    PARTIALLY_PAID,       //已收一部分，還有未收餘額
    PAID                  //已收清
}
