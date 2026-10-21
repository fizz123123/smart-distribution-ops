package com.smartdistribution.masterdata.customerprice.entity;

/**
 * 客戶價格版本狀態 REJECTED、REPLACED、REVOKED 只供歷史查詢
 */
public enum CustomerPriceStatus {
    PENDING_REVIEW,
    ACTIVE,
    REJECTED,
    REPLACED,
    REVOKED
}
