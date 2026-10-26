package com.smartdistribution.inventory.entity;

public enum ReservationEventType {
    RESERVE, //增加預留
    CONSUME, //DO 過帳時使用預留
    RELEASE  //餘量取消時釋放預留
}
