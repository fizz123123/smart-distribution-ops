package com.smartdistribution.audit.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 稽核動作類型。每個動作固定對應一種目標類型，寫入時由動作決定 target_type。
 */
@Getter
@RequiredArgsConstructor
public enum AuditActionType {

    USER_CREATE(AuditTargetType.USER),
    USER_UPDATE(AuditTargetType.USER),
    USER_ACTIVATE(AuditTargetType.USER),
    USER_DISABLE(AuditTargetType.USER),
    //系統管理員替使用者寄出重設密碼連結
    USER_PASSWORD_RESET_SEND(AuditTargetType.USER),
    //閒置超過設定天數，由排程自動停用
    USER_AUTO_DISABLE(AuditTargetType.USER),
    USER_ROLES_REPLACE(AuditTargetType.USER),
    USER_PERMISSION_GRANTS_REPLACE(AuditTargetType.USER),

    SUPPLIER_UPDATE(AuditTargetType.SUPPLIER),
    SUPPLIER_ACTIVATE(AuditTargetType.SUPPLIER),
    SUPPLIER_DEACTIVATE(AuditTargetType.SUPPLIER),

    CUSTOMER_UPDATE(AuditTargetType.CUSTOMER),
    CUSTOMER_ACTIVATE(AuditTargetType.CUSTOMER),
    CUSTOMER_DEACTIVATE(AuditTargetType.CUSTOMER),
    CUSTOMER_PAYMENT_TERM_CHANGE(AuditTargetType.CUSTOMER),

    COMPANY_ITEM_UPDATE(AuditTargetType.COMPANY_ITEM),
    COMPANY_ITEM_ACTIVATE(AuditTargetType.COMPANY_ITEM),
    COMPANY_ITEM_DEACTIVATE(AuditTargetType.COMPANY_ITEM),
    COMPANY_ITEM_UNIT_CONVERSIONS_REPLACE(AuditTargetType.COMPANY_ITEM),
    // 安全庫存屬庫存模組，但稽核目標用公司料號，方便查同一料號的全部異動
    INVENTORY_POLICY_UPDATE(AuditTargetType.COMPANY_ITEM),

    CUSTOMER_ITEM_MAPPING_ACTIVATE(AuditTargetType.CUSTOMER_ITEM_MAPPING),
    CUSTOMER_ITEM_MAPPING_DEACTIVATE(AuditTargetType.CUSTOMER_ITEM_MAPPING);

    private final AuditTargetType targetType;
}
