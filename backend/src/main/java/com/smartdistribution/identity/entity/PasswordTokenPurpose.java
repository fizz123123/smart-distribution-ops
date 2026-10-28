package com.smartdistribution.identity.entity;

public enum PasswordTokenPurpose {
    INVITE, //管理員建立帳號後，使用者第一次設定密碼
    RESET   //使用者忘記密碼，由系統管理員寄出重設連結
}
