package com.smartdistribution.inventory.entity;

public enum ReservationEventType {

    //增加預留
    RESERVE,
    //DO 過帳時使用預留
    CONSUME,
    //餘量取消時釋放預留
    RELEASE
}
