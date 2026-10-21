package com.smartdistribution.masterdata.customer.entity;

/**
 * 客戶付款條件，新客戶預設 DUE_0，只能由財務主管修改
 */
public enum PaymentTerm {
    DUE_0,
    NET_30,
    NET_60
}
