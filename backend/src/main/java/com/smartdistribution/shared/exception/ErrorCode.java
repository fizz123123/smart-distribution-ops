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

    // Concurrency control
    VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "資料已被其他使用者修改，請重新載入後再操作。"
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
