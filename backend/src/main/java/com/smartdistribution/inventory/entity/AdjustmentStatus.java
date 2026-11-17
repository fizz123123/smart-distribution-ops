package com.smartdistribution.inventory.entity;

public enum AdjustmentStatus {
    POSTED,
    PARTIALLY_REVERSED, //部分明細已被沖銷
    REVERSED            //所有明細都已被沖銷
}
