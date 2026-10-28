package com.smartdistribution.identity.service;

import com.smartdistribution.identity.dto.request.ChangeOwnPasswordRequest;
import com.smartdistribution.identity.dto.request.LoginRequest;
import com.smartdistribution.identity.dto.request.SetPasswordRequest;
import com.smartdistribution.identity.dto.response.CurrentUserResponse;
import com.smartdistribution.identity.dto.response.LoginResponse;

/**
 * 使用者本人處理自己的帳號：登入、查詢自己的資料、設定與修改自己的密碼
 * 管理員對別人帳號的操作在 UserManagementService
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    CurrentUserResponse getCurrentUser();

    /**
     * 用信中的連結設定密碼
     * 首次設定：帳號從待啟用轉為啟用；重設密碼：帳號必須仍是啟用中
     */
    void setPassword(SetPasswordRequest request);

    void changeOwnPassword(ChangeOwnPasswordRequest request);
}
