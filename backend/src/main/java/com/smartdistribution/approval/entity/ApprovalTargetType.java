package com.smartdistribution.approval.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 核准目標類型與對應的權限
 * 誰能核准完全由核准權限決定；MANAGER 可核准的範圍由權限初始化資料設定
 * 查看權限用來決定誰能看核准歷程：看得到這筆資料的人，就能看它的送審與退回紀錄
 */
@Getter
@RequiredArgsConstructor
public enum ApprovalTargetType {

    CUSTOMER("客戶", "CUSTOMER_APPROVE", "CUSTOMER_VIEW"),
    SUPPLIER("供應商", "SUPPLIER_APPROVE", "SUPPLIER_VIEW"),
    COMPANY_ITEM("公司料號", "COMPANY_ITEM_APPROVE", "COMPANY_ITEM_VIEW"),
    CUSTOMER_ITEM_MAPPING("客戶料號對應", "CUSTOMER_ITEM_MAPPING_APPROVE", "CUSTOMER_ITEM_MAPPING_VIEW"),
    CUSTOMER_PRICE("客戶價格", "CUSTOMER_PRICE_APPROVE", "CUSTOMER_PRICE_VIEW"),
    SALES_ORDER("銷售訂單", "SO_APPROVE", "SO_VIEW"),
    //取消申請沒有獨立的查看權限，看得到 SO 就看得到它的取消申請
    SO_CANCELLATION("SO 餘量取消", "SO_CANCELLATION_APPROVE", "SO_VIEW"),
    PURCHASE_ORDER("採購單", "PO_APPROVE", "PO_VIEW");

    //通知信上顯示的類型名稱
    private final String displayName;
    private final String permissionCode;
    private final String viewPermissionCode;
}
