package com.smartdistribution.shared.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Authentication
    AUTHENTICATION_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "請先登入後再執行此操作。"
    ),
    AUTH_INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "帳號或密碼錯誤。"
    ),
    AUTH_TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "登入憑證無效，請重新登入。"
    ),
    AUTH_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "登入憑證已過期，請重新登入。"
    ),

    // Authorization
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "你沒有執行此操作的權限。"
    ),

    // User account
    USER_DISABLED(
            HttpStatus.UNAUTHORIZED,
            "此帳號已停用。"
    ),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的使用者。"
    ),
    USER_EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "此 Email 已由其他使用者使用。"
    ),
    USER_SELF_MANAGEMENT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "不得修改自己的帳號狀態或授權資料。"
    ),
    APP_ADMIN_AUTHORIZATION_IMMUTABLE(
            HttpStatus.FORBIDDEN,
            "APP_ADMIN 為固定系統管理角色，不能透過 API 指派或修改其授權。"
    ),
    USER_MANAGEMENT_PERMISSION_NOT_GRANTABLE(
            HttpStatus.BAD_REQUEST,
            "帳號與授權管理權限不可作為個別權限授予。"
    ),

    // Role and permission management
    ROLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的角色。"
    ),
    PERMISSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的權限。"
    ),

    // Approval workflow
    APPROVAL_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的核准申請。"
    ),
    APPROVAL_ALREADY_PENDING(
            HttpStatus.CONFLICT,
            "此資料已有待處理的核准申請。"
    ),
    APPROVAL_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "此核准申請已處理，請重新載入。"
    ),
    APPROVAL_SELF_APPROVAL_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "建立者或送審者不得核准自己的申請。"
    ),

    // Master data
    SUPPLIER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的供應商。"
    ),
    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的客戶。"
    ),
    COMPANY_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的公司料號。"
    ),
    COMPANY_ITEM_CODE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "公司料號已存在。"
    ),
    COMPANY_ITEM_PREFERRED_SUPPLIER_INVALID(
            HttpStatus.BAD_REQUEST,
            "偏好供應商不存在或尚未啟用。"
    ),
    COMPANY_ITEM_UNIT_CONVERSION_INVALID(
            HttpStatus.BAD_REQUEST,
            "單位換算不可包含庫存基準單位，也不可重複設定同一單位。"
    ),
    CUSTOMER_ITEM_MAPPING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的客戶料號對應。"
    ),
    CUSTOMER_ITEM_MAPPING_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "此客戶已有相同客戶料號的對應。"
    ),
    CUSTOMER_ITEM_MAPPING_REFERENCE_INACTIVE(
            HttpStatus.CONFLICT,
            "客戶或公司料號不存在或尚未啟用。"
    ),
    CUSTOMER_PRICE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的客戶價格。"
    ),
    CUSTOMER_PRICE_PENDING_EXISTS(
            HttpStatus.CONFLICT,
            "此客戶料號與計價單位已有待覆核的價格，請先完成覆核或拒絕。"
    ),
    CUSTOMER_PRICE_MAPPING_INACTIVE(
            HttpStatus.CONFLICT,
            "客戶料號對應、客戶或公司料號尚未啟用或已停用。"
    ),
    CUSTOMER_PRICE_UNIT_INVALID(
            HttpStatus.CONFLICT,
            "計價單位必須是料號的庫存基準單位或已設定換算的單位。"
    ),
    COMPANY_ITEM_UNIT_CONVERSIONS_EXIST(
            HttpStatus.CONFLICT,
            "已設定其他單位換算，請先清除換算再修改庫存基準單位。"
    ),
    MASTER_DATA_TAX_ID_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "統一編號已被其他資料使用。"
    ),
    MASTER_DATA_IDENTITY_LOCKED(
            HttpStatus.CONFLICT,
            "已啟用的資料不可修改名稱或統一編號。"
    ),
    MASTER_DATA_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "目前狀態不允許此操作，請重新載入。"
    ),

    // Sales order
    SALES_ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的銷售訂單。"
    ),
    SO_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "銷售訂單目前的狀態不允許此操作，請重新載入。"
    ),
    SO_CUSTOMER_INACTIVE(
            HttpStatus.CONFLICT,
            "客戶尚未啟用或已停用，不能用於銷售訂單。"
    ),
    SO_RESPONSIBLE_SALES_INVALID(
            HttpStatus.BAD_REQUEST,
            "負責業務必須是啟用中且具有業務角色的使用者。"
    ),
    SO_MAPPING_CUSTOMER_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "客戶料號不屬於這張銷售訂單的客戶。"
    ),
    SO_LINE_INVALID(
            HttpStatus.BAD_REQUEST,
            "銷售訂單明細不正確。"
    ),
    SO_DELIVERY_INFO_INCOMPLETE(
            HttpStatus.BAD_REQUEST,
            "請填寫要求交期與交貨方式；外送時另需交貨地址、收貨聯絡人與電話。"
    ),
    SO_LINES_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "銷售訂單至少要有一筆明細才能送審。"
    ),
    SO_MASTER_DATA_INACTIVE(
            HttpStatus.CONFLICT,
            "銷售訂單引用的客戶、客戶料號對應或公司料號尚未啟用或已停用。"
    ),
    SO_UNIT_INVALID(
            HttpStatus.CONFLICT,
            "訂購單位必須是庫存基準單位或已設定換算的單位。"
    ),
    SO_ACTIVE_PRICE_REQUIRED(
            HttpStatus.CONFLICT,
            "尚無已覆核的客戶價格，無法送審。"
    ),
    SO_PRICE_REVOKED(
            HttpStatus.CONFLICT,
            "引用的價格已撤銷，請退回後依目前價格重新送審。"
    ),

    // Sales order cancellation
    SALES_ORDER_CANCELLATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的 SO 取消申請。"
    ),
    SO_CANCELLATION_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "SO 取消申請目前的狀態不允許此操作，請重新載入。"
    ),
    SO_CANCELLATION_PENDING_EXISTS(
            HttpStatus.CONFLICT,
            "這張 SO 已有尚未完成的取消申請，請先送審、撤回或等待核准結果。"
    ),
    SO_CANCELLATION_QUANTITY_EXCEEDED(
            HttpStatus.CONFLICT,
            "取消數量超過目前可取消的未出貨餘量。"
    ),

    // Inventory
    INVENTORY_ADJUSTMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到指定的庫存調整。"
    ),
    INVENTORY_ADJUSTMENT_LINES_INVALID(
            HttpStatus.BAD_REQUEST,
            "調整明細不正確：同一料號只能出現一次，數量不可為 0 且須符合單位的小數位。"
    ),
    INVENTORY_OPENING_BALANCE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "期初庫存只能為正數，且只能在料號尚無任何庫存異動或預留時建立一次。"
    ),
    INVENTORY_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "調整後可出貨實體數量會小於 0。"
    ),
    INVENTORY_RESERVATION_WOULD_BE_BREACHED(
            HttpStatus.CONFLICT,
            "調減後可出貨實體數量會低於已預留數量。"
    ),
    INVENTORY_ADJUSTMENT_NOT_REVERSIBLE(
            HttpStatus.CONFLICT,
            "此調整已更正過，或本身就是更正紀錄，不能再更正。"
    ),

    // Concurrency control
    VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "資料已被其他使用者修改，請重新載入後再操作。"
    ),
    DATA_CONFLICT(
            HttpStatus.CONFLICT,
            "資料與現有資料重複或衝突，請重新載入後再操作。"
    ),

    // Request validation and parsing
    VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "輸入資料格式不正確。"
    ),
    MALFORMED_REQUEST(
            HttpStatus.BAD_REQUEST,
            "無法解析請求內容。"
    ),

    // HTTP request routing
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "找不到請求的資源。"
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "此資源不支援目前的請求方法。"
    ),

    // Unexpected server error
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "系統發生未預期的錯誤。"
    );

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
