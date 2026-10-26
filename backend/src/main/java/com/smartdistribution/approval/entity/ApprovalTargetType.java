package com.smartdistribution.approval.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 核准目標類型與對應的核准權限
 * 誰能核准完全由權限決定；MANAGER 可核准的範圍由權限初始化資料設定
 */
@Getter
@RequiredArgsConstructor
public enum ApprovalTargetType {

    CUSTOMER("CUSTOMER_APPROVE"),
    SUPPLIER("SUPPLIER_APPROVE"),
    COMPANY_ITEM("COMPANY_ITEM_APPROVE"),
    CUSTOMER_ITEM_MAPPING("CUSTOMER_ITEM_MAPPING_APPROVE"),
    CUSTOMER_PRICE("CUSTOMER_PRICE_APPROVE"),
    SALES_ORDER("SO_APPROVE"),
    SO_CANCELLATION("SO_CANCELLATION_APPROVE");

    private final String permissionCode;
}
