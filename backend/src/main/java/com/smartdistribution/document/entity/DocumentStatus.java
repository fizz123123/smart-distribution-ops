package com.smartdistribution.document.entity;

public enum DocumentStatus {
    ACTIVE,
    VOIDED //單據取消時作廢；檔名加上 -voided 後綴，仍可下載
}
