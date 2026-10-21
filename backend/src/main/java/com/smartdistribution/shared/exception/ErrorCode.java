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
