package com.smartdistribution.inventory.entity;

/**
 * 代表：為什麼要調整
 */
public enum AdjustmentReasonType {
    OPENING_BALANCE, //只允許正數
    COUNT_DIFFERENCE,
    DAMAGE,
    OTHER
}
