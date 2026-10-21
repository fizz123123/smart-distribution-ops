package com.smartdistribution.masterdata.common;

/**
 * 主檔狀態 (客戶、供應商、公司料號、客戶料號對應)
 */
public enum MasterDataStatus {
    DRAFT,
    PENDING_APPROVAL,
    RETURNED,
    ACTIVE,
    INACTIVE
}
